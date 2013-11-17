package ru.trylogic.gom.config.dsl

import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.CompilationUnit
import ru.trylogic.gom.GOM
import ru.trylogic.gom.Transformer
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.GOMConfig.Mapping
import ru.trylogic.gom.config.GOMConfig.Mapping.Field

import static groovyjarjarasm.asm.Opcodes.*;

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;
import static org.codehaus.groovy.ast.Parameter.EMPTY_ARRAY;

class MapperProcessor implements CompilationUnitAware {

    public static final String VALUE_OF = "valueOf"
    public static final String TO_STRING = "toString"
    CompilationUnit compilationUnit;

    MapperProcessor(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit
    }

    void process(ClassNode classNode, GOMConfig config) {
        classNode.superClass = ClassHelper.makeWithoutCaching(DSLConfigBuilderBase, false);

        classNode.modifiers |= ACC_FINAL;

        Set<InnerClassNode> transformers = config.mappings.collect { processMapping(config, classNode, it) }
        
        transformers.each(classNode.module.&addClass);

        classNode.addMethod(generateBuildMethod(transformers));
    }
    
    String getAFieldConverterName(Field field) {
        return field.aName + "FromB";
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


        mapperClassNode.addProperty("gom", ACC_PUBLIC, ClassHelper.makeWithoutCaching(GOM), null, null, null);

        ClassNode aClassNode = ClassHelper.makeWithoutCaching(mapping.a);
        ClassNode bClassNode = ClassHelper.makeWithoutCaching(mapping.b);
        
        def transformerInterfaceClassNode = ClassHelper.makeWithoutCaching(Transformer, false);
        transformerInterfaceClassNode.usingGenerics = true;
        transformerInterfaceClassNode.genericsTypes = [new GenericsType(aClassNode), new GenericsType(bClassNode)];
        mapperClassNode.addInterface(transformerInterfaceClassNode);

        mapperClassNode.addMethod(generateTypeGetter("getSourceType", aClassNode));
        mapperClassNode.addMethod(generateTypeGetter("getTargetType", bClassNode));


        MethodNode toAMethod;
        
        if(mapping.toA != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(mapping.toA);

            toAMethod = new MethodNode("toA", ACC_PUBLIC, aClassNode, closure.parameters, null, closure.code);
        } else {
            toAMethod = generateToAMethod(config, mapperClassNode, mapping, aClassNode, bClassNode);
        }
        
        mapperClassNode.addMethod(toAMethod);
        
        return mapperClassNode;
    }

    MethodNode generateTypeGetter(String name, ClassNode node) {
        ClassNode nodeClass = ClassHelper.makeWithoutCaching(Class, false);
        nodeClass.usingGenerics = true;
        nodeClass.genericsTypes = [new GenericsType(node)];

        return new MethodNode(name, ACC_PUBLIC, nodeClass, EMPTY_ARRAY, null, new ReturnStatement(new ClassExpression(node)));
    }

    MethodNode generateBuildMethod(Set<InnerClassNode> transformers) {
        def resultClassNode = ClassHelper.makeWithoutCaching(HashSet, false);

        def methodBody = new BlockStatement();
        def methodNode = new MethodNode("getMappers", ACC_PUBLIC, ClassHelper.makeWithoutCaching(Collection, false), EMPTY_ARRAY, null, methodBody)
        def resultVariable = new VariableExpression("result", resultClassNode)

        methodBody.statements << declStatement(resultVariable, new ConstructorCallExpression(resultClassNode, EMPTY_ARGUMENTS))

        transformers.each {
            it.enclosingMethod = methodNode;
            methodBody.statements << new ExpressionStatement(new MethodCallExpression(resultVariable, "add", new ConstructorCallExpression(it, EMPTY_ARGUMENTS)));
        }

        methodBody.statements << new ReturnStatement(resultVariable);

        return methodNode
    }

    MethodNode generateToAMethod(GOMConfig config, InnerClassNode mapperClassNode, Mapping mapping, ClassNode aClassNode, ClassNode bClassNode) {
        def methodBody = new BlockStatement();

        def resultVariable = new VariableExpression("result", aClassNode)
        def bParameter = new Parameter(bClassNode, "b")
        methodBody.statements << declStatement(resultVariable, new ConstructorCallExpression(aClassNode, EMPTY_ARGUMENTS));

        aClassNode.fields.each { aField ->
            if((aField.modifiers & ACC_SYNTHETIC) ) {
                return;
            }

            Expression value = generateFieldAssign(config, mapping, mapperClassNode, aClassNode, bClassNode, aField, bParameter);

            if(value == null) {
                return;
            }


            def propertyExpression = new PropertyExpression(resultVariable, aField.name)
            methodBody.statements << assignStatement(propertyExpression, value);
        }

        methodBody.statements << new ReturnStatement(resultVariable)

        return new MethodNode("toA", ACC_PUBLIC, aClassNode, [bParameter] as Parameter[], null, methodBody)
    }
    
    
    Expression generateFieldAssign(GOMConfig config, Mapping mapping, InnerClassNode mapperClassNode, ClassNode aClassNode, ClassNode bClassNode, FieldNode aField, Parameter bParameter) {
        Field fieldConfig = mapping.fields?.find { it.aName == aField.name }
        if(fieldConfig?.a != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(fieldConfig.a);

            def methodName = getAFieldConverterName(fieldConfig)
            mapperClassNode.addMethod(methodName, ACC_PUBLIC, aField.type, closure.parameters, null, closure.code);

            return new MethodCallExpression(THIS_EXPRESSION, methodName, new ArgumentListExpression(bParameter));
        }

        FieldNode bField = bClassNode.getField(fieldConfig?.bName ?: aField.name);
        
        if(bField == null) {
            return null;
        }
        
        def bFieldValue = new PropertyExpression(new VariableExpression(bParameter), bField.name)

        def value = generateFieldValue(config, aField.type, bField.type, bFieldValue)
        
        if(value == null) {
            return null;
        }

        return new TernaryExpression(notNullExpr(bFieldValue), value, ConstantExpression.NULL);
    }
    
    Expression generateFieldValue(GOMConfig config, ClassNode aFieldType, ClassNode bFieldType, PropertyExpression bFieldValue) {
        if(aFieldType.isDerivedFrom(bFieldType)) {
            return bFieldValue;
        }

        def unwrappedAFieldType = ClassHelper.getUnwrapper(aFieldType)
        
        if(config.mappings.any { it.a.name.equalsIgnoreCase(aFieldType.name) && it.b.name.equalsIgnoreCase(bFieldType.name)}) {
            return generateKnownMappingFieldValue(aFieldType, bFieldType, bFieldValue)
        }

        switch(unwrappedAFieldType) {
            case {it.enum}:
                return generateEnumFieldValue(aFieldType, bFieldType, bFieldValue);
            case {ClassHelper.isPrimitiveType(it)}:
                return generatePrimitiveFieldValue(aFieldType, bFieldValue);
            case ClassHelper.STRING_TYPE:
                return generateStringFieldValue(bFieldValue);
        }

        //TODO warn about no mapping
        return bFieldValue;
    }
    
    Expression generateKnownMappingFieldValue(ClassNode aFieldType, ClassNode bFieldType, PropertyExpression bFieldValue) {
        def mapper = new MethodCallExpression(new PropertyExpression(THIS_EXPRESSION, "gom"), "getTransformer", new ArgumentListExpression(new ClassExpression(aFieldType), new ClassExpression(bFieldType)));
    
        return new MethodCallExpression(mapper, "toA", new ArgumentListExpression(bFieldValue));
    }

    Expression generateStringFieldValue(PropertyExpression bFieldValue) {
        new MethodCallExpression(bFieldValue, TO_STRING, EMPTY_ARGUMENTS)
    }

    Expression generatePrimitiveFieldValue(ClassNode aFieldType, PropertyExpression bFieldValue) {
        new StaticMethodCallExpression(ClassHelper.getWrapper(aFieldType), VALUE_OF, new ArgumentListExpression(bFieldValue))
    }

    Expression generateEnumFieldValue(ClassNode aFieldType, ClassNode bFieldType, PropertyExpression bFieldValue) {
        Expression enumKey = null;
        switch(bFieldType) {
            case {it.enum}:
                enumKey = new MethodCallExpression(bFieldValue, "name", EMPTY_ARGUMENTS);
                break;
            case ClassHelper.STRING_TYPE:
                enumKey = bFieldValue;
                break;
        }
        if(enumKey == null) {
            return null;
        }
        return new StaticMethodCallExpression(aFieldType, VALUE_OF, new ArgumentListExpression(new ClassExpression(aFieldType), enumKey));
    }
    
}
