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

    static enum Direction {
        A("toA"),
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
            return ab(mapping?.a?.name, mapping?.b?.name)
        }
        
        String getSourceClassName(Mapping mapping) {
            return ab(mapping?.b?.name, mapping?.a?.name)
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

    MapperProcessor(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit
    }

    Set<InnerClassNode> process(ClassNode classNode, GOMConfig config) {
        return config.mappings.collect { processMapping(config, classNode, it) }
    }
    
    InnerClassNode processMapping(GOMConfig config, ClassNode classNode, Mapping mapping) {
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

        mapperClassNode.addMethod(generateToMethod(Direction.A, config, mapperClassNode, mapping, aClassNode, bClassNode));
        mapperClassNode.addMethod(generateToMethod(Direction.B, config, mapperClassNode, mapping, bClassNode, aClassNode));
        
        return mapperClassNode;
    }

    MethodNode generateTypeGetter(String name, ClassNode node) {
        ClassNode nodeClass = ClassHelper.makeWithoutCaching(Class, false);
        nodeClass.usingGenerics = true;
        nodeClass.genericsTypes = [new GenericsType(node)];

        return new MethodNode(name, ACC_PUBLIC, nodeClass, EMPTY_ARRAY, null, new ReturnStatement(new ClassExpression(node)));
    }

    MethodNode generateToMethod(Direction direction, GOMConfig config, InnerClassNode mapperClassNode, Mapping mapping, ClassNode targetClassNode, ClassNode sourceClassNode) {
        def toMethodCode = direction.toMethodCode(mapping)
        if(toMethodCode != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(toMethodCode);

            return new MethodNode(direction.toMethodName, ACC_PUBLIC, targetClassNode, closure.parameters, null, closure.code);
        }

        def methodBody = new BlockStatement();

        def targetVariable = new VariableExpression('$result', targetClassNode)
        def sourceParameter = new Parameter(sourceClassNode, '$source')
        methodBody.statements << declStatement(targetVariable, new ConstructorCallExpression(targetClassNode, EMPTY_ARGUMENTS));

        targetClassNode.fields.each { targetField ->
            if((targetField.modifiers & ACC_SYNTHETIC) ) {
                return;
            }

            Expression value = generateFieldAssign(direction, config, mapping, mapperClassNode, targetClassNode, sourceClassNode, targetField, sourceParameter);

            if(value == null) {
                return;
            }

            def propertyExpression = new PropertyExpression(targetVariable, targetField.name)
            methodBody.statements << assignStatement(propertyExpression, value);
        }

        methodBody.statements << new ReturnStatement(targetVariable)

        return new MethodNode(direction.toMethodName, ACC_PUBLIC, targetClassNode, [sourceParameter] as Parameter[], null, methodBody)
    }
    
    Expression generateFieldAssign(Direction direction, GOMConfig config, Mapping mapping, InnerClassNode mapperClassNode, ClassNode targetClassNode, ClassNode sourceClassNode, FieldNode targetField, Parameter sourceParameter) {
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

        def value = generateFieldValue(direction, config, targetField.type, sourceField.type, sourceFieldValue)
        
        if(value == null) {
            return null;
        }

        return new TernaryExpression(notNullExpr(sourceFieldValue), value, ConstantExpression.NULL);
    }
    
    Expression generateFieldValue(Direction direction, GOMConfig config, ClassNode targetFieldType, ClassNode sourceFieldType, PropertyExpression sourceFieldValue) {
        if(targetFieldType.isDerivedFrom(sourceFieldType)) {
            return sourceFieldValue;
        }

        def unwrappedAFieldType = ClassHelper.getUnwrapper(targetFieldType)
        
        if(config.mappings.any { direction.getTargetClassName(it).equalsIgnoreCase(targetFieldType.name) && direction.getSourceClassName(it).equalsIgnoreCase(sourceFieldType.name)}) {
            return generateKnownMappingFieldValue(direction, targetFieldType, sourceFieldType, sourceFieldValue)
        }

        switch(unwrappedAFieldType) {
            case {it.enum}:
                return generateEnumFieldValue(targetFieldType, sourceFieldType, sourceFieldValue);
            case {ClassHelper.isPrimitiveType(it)}:
                return generatePrimitiveFieldValue(targetFieldType, sourceFieldValue);
            case ClassHelper.STRING_TYPE:
                return generateStringFieldValue(sourceFieldValue);
        }

        //TODO warn about no mapping
        return sourceFieldValue;
    }
    
    Expression generateKnownMappingFieldValue(Direction direction, ClassNode targetFieldType, ClassNode sourceFieldType, PropertyExpression bFieldValue) {
        //TODO caching
        def mapper = new MethodCallExpression(new PropertyExpression(THIS_EXPRESSION, GOM_FIELD_NAME), "getTransformer", new ArgumentListExpression(new ClassExpression(targetFieldType), new ClassExpression(sourceFieldType)));
    
        return new MethodCallExpression(mapper, direction.toMethodName, new ArgumentListExpression(bFieldValue));
    }

    Expression generateStringFieldValue(PropertyExpression sourceFieldValue) {
        new MethodCallExpression(sourceFieldValue, TO_STRING, EMPTY_ARGUMENTS)
    }

    Expression generatePrimitiveFieldValue(ClassNode targetFieldType, PropertyExpression sourceFieldValue) {
        new StaticMethodCallExpression(ClassHelper.getWrapper(targetFieldType), VALUE_OF, new ArgumentListExpression(sourceFieldValue))
    }

    Expression generateEnumFieldValue(ClassNode targetFieldType, ClassNode sourceFieldType, PropertyExpression sourceFieldValue) {
        Expression enumKey = null;
        switch(sourceFieldType) {
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
