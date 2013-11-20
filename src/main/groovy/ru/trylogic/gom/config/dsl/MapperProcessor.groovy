package ru.trylogic.gom.config.dsl

import groovy.transform.CompilationUnitAware
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.CompilationUnit
import ru.trylogic.gom.GOM
import ru.trylogic.gom.Transformer
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.GOMConfig.Mapping
import ru.trylogic.gom.config.GOMConfig.Mapping.Field

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;
import static org.codehaus.groovy.ast.Parameter.EMPTY_ARRAY;

class MapperProcessor implements CompilationUnitAware, Opcodes {

    public static final String VALUE_OF = "valueOf"
    public static final String TO_STRING = "toString"
    public static final String GOM_FIELD_NAME = "gom"
    public static final String TO_A_METHOD_NAME = "toA"

    static enum Direction {
        A(TO_A_METHOD_NAME),
        B("toB")
        
        String toMethodName;

        Direction(String toMethodName) {
            this.toMethodName = toMethodName
        }

        String getFieldConverterName(Field field) {
            return ab(field.aName, field.bName)  + "From" + this.name();
        }
        
        String getFieldConverterCode(Field field) {
            return ab(field?.a, field?.b);
        }
        
        String toMethodCode(Mapping mapping) {
            return ab(mapping.toA, mapping.toB);
        }

        String getTargetFieldName(Field field) {
            return ab(field?.bName, field?.aName);
        }

        String getSourceFieldName(Field field) {
            return ab(field?.aName, field?.bName);
        }
        
        String getTargetClassName(Mapping mapping) {
            return ab(mapping?.a, mapping?.b).name
        }
        
        String getSourceClassName(Mapping mapping) {
            return ab(mapping?.b, mapping?.a).name
        }
        
        def <T> T ab(T a, T b) {
            switch(this) {
                case A:
                    return a;
                case B:
                    return b;
                default:
                    throw new Exception("unreachable");
            }
        }
    }
    
    CompilationUnit compilationUnit;
    
    GOMConfig config;

    MapperProcessor(CompilationUnit compilationUnit, GOMConfig config) {
        this.compilationUnit = compilationUnit
        this.config = config
    }

    Set<InnerClassNode> process(ClassNode classNode) {
        return config.mappings.collect { processMapping(classNode, it) }
    }
    
    InnerClassNode processMapping(ClassNode classNode, Mapping mapping) {
        int counter = 0;

        String className;
        while (true) {
            counter++;
            className = classNode.getName() + '$' + counter

            if (!classNode.innerClasses.any { it.name.equalsIgnoreCase(className) }) {
                break;
            }
        }

        final InnerClassNode mapperClassNode = new InnerClassNode(classNode, className, ACC_PUBLIC | ACC_STATIC, ClassHelper.OBJECT_TYPE);
        mapperClassNode.anonymous = true;

        mapperClassNode.addProperty(GOM_FIELD_NAME, ACC_PUBLIC, ClassHelper.makeWithoutCaching(GOM), null, null, null);

        ClassNode aClassNode = ClassHelper.makeWithoutCaching(mapping.a);
        ClassNode bClassNode = ClassHelper.makeWithoutCaching(mapping.b);
        
        def transformerInterfaceClassNode = ClassHelper.makeWithoutCaching(Transformer, false);
        transformerInterfaceClassNode.usingGenerics = true;
        transformerInterfaceClassNode.genericsTypes = [new GenericsType(aClassNode), new GenericsType(bClassNode)];
        mapperClassNode.addInterface(transformerInterfaceClassNode);

        mapperClassNode.addMethod(generateTypeGetter("getSourceType", aClassNode));
        mapperClassNode.addMethod(generateTypeGetter("getTargetType", bClassNode));

        mapperClassNode.addMethod(generateToMethod(Direction.A, mapperClassNode, mapping, aClassNode, bClassNode));
        mapperClassNode.addMethod(generateToMethod(Direction.B, mapperClassNode, mapping, bClassNode, aClassNode));
        
        return mapperClassNode;
    }

    MethodNode generateTypeGetter(String name, ClassNode node) {
        ClassNode nodeClass = ClassHelper.makeWithoutCaching(Class, false);
        nodeClass.usingGenerics = true;
        nodeClass.genericsTypes = [new GenericsType(node)];

        return new MethodNode(name, ACC_PUBLIC, nodeClass, EMPTY_ARRAY, null, new ReturnStatement(new ClassExpression(node)));
    }

    MethodNode generateToMethod(Direction direction, InnerClassNode mapperClassNode, Mapping mapping, ClassNode targetClassNode, ClassNode sourceClassNode) {
        def toMethodCode = direction.toMethodCode(mapping)
        if(toMethodCode != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(toMethodCode);

            return new MethodNode(direction.toMethodName, ACC_PUBLIC, targetClassNode, closure.parameters, null, closure.code);
        }

        def methodBody = new BlockStatement();

        def sourceParameter = new Parameter(sourceClassNode, '$source')
        
        methodBody.statements << new IfStatement(equalsNullExpr(new VariableExpression(sourceParameter)), new BlockStatement([new ReturnStatement(new ConstantExpression(null))], new VariableScope()), new EmptyStatement());
        
        def targetVariable = new VariableExpression('$result', targetClassNode)
        methodBody.statements << declStatement(targetVariable, new ConstructorCallExpression(targetClassNode, EMPTY_ARGUMENTS));

        targetClassNode.fields.each { targetField ->
            if((targetField.modifiers & ACC_SYNTHETIC) ) {
                return;
            }

            Expression value = generateFieldAssign(direction, mapping, mapperClassNode, targetClassNode, sourceClassNode, targetField, sourceParameter);

            if(value == null) {
                return;
            }

            def propertyExpression = new PropertyExpression(targetVariable, targetField.name)
            methodBody.statements << assignStatement(propertyExpression, value);
        }

        methodBody.statements << new ReturnStatement(targetVariable)

        return new MethodNode(direction.toMethodName, ACC_PUBLIC, targetClassNode, [sourceParameter] as Parameter[], null, methodBody)
    }
    
    Expression generateFieldAssign(Direction direction, Mapping mapping, InnerClassNode mapperClassNode, ClassNode targetClassNode, ClassNode sourceClassNode, FieldNode targetField, Parameter sourceParameter) {
        Field fieldConfig = mapping.fields?.find { direction.getSourceFieldName(it) == targetField.name }
        String sourceFieldConverterCode = direction.getFieldConverterCode(fieldConfig);
        if(sourceFieldConverterCode != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(sourceFieldConverterCode);

            def methodName = direction.getFieldConverterName(fieldConfig)
            mapperClassNode.addMethod(methodName, ACC_PUBLIC, targetField.type, closure.parameters, null, closure.code);

            return new MethodCallExpression(THIS_EXPRESSION, methodName, new ArgumentListExpression(sourceParameter));
        }

        FieldNode sourceField = sourceClassNode.getField(direction.getTargetFieldName(fieldConfig) ?: targetField.name);
        
        if(sourceField == null) {
            return null;
        }
        
        def sourceFieldValue = new PropertyExpression(new VariableExpression(sourceParameter), sourceField.name)
        sourceFieldValue.type = sourceClassNode.getField(sourceField.name).type
        
        def value = generateFieldValue(mapperClassNode, targetField.type, sourceFieldValue)
        
        if(value == null) {
            return null;
        }

        return new TernaryExpression(notNullExpr(sourceFieldValue), value, ConstantExpression.NULL);
    }
    
    static boolean is(ClassNode type, ClassNode to) {
        return type.isDerivedFrom(to) || type.implementsInterface(to);
    }
    
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        def sourceFieldType = sourceFieldValue.type;
        if(is(targetFieldType, ClassHelper.LIST_TYPE) || is(targetFieldType, ClassHelper.makeWithoutCaching(Set, false))) {
            if(!is(sourceFieldType, ClassHelper.makeWithoutCaching(Iterable, false))) {
                return null;
            }
            if(sourceFieldType.genericsTypes == null || sourceFieldType.genericsTypes.size() != 1) {
                return null;
            }
            
            return generateCollectionFieldValue(mapperClassNode, targetFieldType, sourceFieldValue);
        }
        
        if(targetFieldType.isDerivedFrom(sourceFieldType)) {
            boolean genericTypesMatch = true;

            def targetFieldGenericTypes = targetFieldType.genericsTypes
            def sourceFieldGenericTypes = sourceFieldType.genericsTypes
            for(int i = 0; i < targetFieldGenericTypes?.size(); i++) {
                genericTypesMatch &= targetFieldGenericTypes[i].type == sourceFieldGenericTypes[i].type;
            }
            if(genericTypesMatch) {
                return sourceFieldValue
            }
        }

        if(config.mappings.any { it.a.name.equalsIgnoreCase(targetFieldType.name) && it.b.name.equalsIgnoreCase(sourceFieldType.name)}) {
            return generateKnownMappingFieldValue(targetFieldType, sourceFieldValue)
        }

        def unwrappedAFieldType = ClassHelper.getUnwrapper(targetFieldType)

        switch(unwrappedAFieldType) {
            case {it.enum}:
                return generateEnumFieldValue(targetFieldType, sourceFieldValue);
            case {ClassHelper.isPrimitiveType(it)}:
                return generatePrimitiveFieldValue(targetFieldType, sourceFieldValue);
            case ClassHelper.STRING_TYPE:
                return generateStringFieldValue(sourceFieldValue);
        }

        //TODO warn about no mapping
        return null;
    }
    
    Expression generateCollectionFieldValue(InnerClassNode mapperClassNode, ClassNode targetListType, Expression sourceFieldValue) {
        ClassNode resultVariableType;
        switch(targetListType) {
            case ClassHelper.LIST_TYPE:
                resultVariableType = ClassHelper.makeWithoutCaching(ArrayList, false);
                break;
            case ClassHelper.makeWithoutCaching(Set, false):
                resultVariableType = ClassHelper.makeWithoutCaching(HashSet, false);
                break;
            default:
                resultVariableType = targetListType;
        }

        resultVariableType.usingGenerics = true;
        resultVariableType.genericsTypes = targetListType.genericsTypes

        def resultVariable = new VariableExpression('$result', resultVariableType)

        Parameter sourceParameter = new Parameter(sourceFieldValue.type, '$source')
        
        def methodCode = new BlockStatement();
        methodCode.statements << declStatement(resultVariable, new ConstructorCallExpression(resultVariable.originType, EMPTY_ARGUMENTS));
        
        def loopBlock = new BlockStatement();

        def iParameter = new Parameter(sourceParameter.type.genericsTypes.first().type, '$item');
        
        def value = generateFieldValue(mapperClassNode, resultVariable.originType.genericsTypes.first().type, new VariableExpression(iParameter))
        
        loopBlock.statements << new ExpressionStatement(new MethodCallExpression(resultVariable, "add", value));
        methodCode.statements << new ForStatement(iParameter, new VariableExpression(sourceParameter), loopBlock);
        methodCode.statements << new ReturnStatement(resultVariable);
        
        def method = mapperClassNode.addMethod("converter" + System.currentTimeMillis(), ACC_PUBLIC, resultVariable.originType, [sourceParameter] as Parameter[], null, methodCode);
        return new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue);
    }
    
    Expression generateKnownMappingFieldValue(ClassNode targetFieldType, Expression sourceFieldValue) {
        //TODO caching

        def targetClassExpression = new ClassExpression(targetFieldType)
        def sourceClassExpression = new ClassExpression(sourceFieldValue.type)

        def mapper = new MethodCallExpression(new PropertyExpression(THIS_EXPRESSION, GOM_FIELD_NAME), "getTransformer", new ArgumentListExpression(targetClassExpression, sourceClassExpression));
    
        return new MethodCallExpression(mapper, TO_A_METHOD_NAME, new ArgumentListExpression(sourceFieldValue));
    }

    Expression generateStringFieldValue(Expression sourceFieldValue) {
        new MethodCallExpression(sourceFieldValue, TO_STRING, EMPTY_ARGUMENTS)
    }

    Expression generatePrimitiveFieldValue(ClassNode targetFieldType, Expression sourceFieldValue) {
        new StaticMethodCallExpression(ClassHelper.getWrapper(targetFieldType), VALUE_OF, new ArgumentListExpression(sourceFieldValue))
    }

    Expression generateEnumFieldValue(ClassNode targetFieldType, Expression sourceFieldValue) {
        Expression enumKey = null;
        switch(sourceFieldValue.type) {
            case {it.enum}:
                enumKey = new MethodCallExpression(sourceFieldValue, "name", EMPTY_ARGUMENTS);
                break;
            case ClassHelper.STRING_TYPE:
                enumKey = sourceFieldValue;
                break;
        }
        if(enumKey == null) {
            return null;
        }
        return new StaticMethodCallExpression(targetFieldType, VALUE_OF, new ArgumentListExpression(new ClassExpression(targetFieldType), enumKey));
    }
    
}
