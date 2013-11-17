package ru.trylogic.gom.config.dsl

import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.syntax.*
import ru.trylogic.gom.Transformer
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.GOMConfig.Mapping
import ru.trylogic.gom.config.GOMConfig.Mapping.Field

import static groovyjarjarasm.asm.Opcodes.*

class MapperProcessor implements CompilationUnitAware {
    
    CompilationUnit compilationUnit;

    MapperProcessor(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit
    }

    void process(ClassNode classNode, GOMConfig config) {
        classNode.superClass = ClassHelper.makeWithoutCaching(DSLConfigBuilderBase, false);

        classNode.modifiers |= ACC_FINAL;

        Set<InnerClassNode> transformers = config.mappings.collect { processMapping(classNode, it) }
        
        transformers.each(classNode.module.&addClass);

        classNode.addMethod(generateBuildMethod(transformers));
    }
    
    String getAFieldConverterName(Field field) {
        return field.aName + "FromB";
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

        ClassNode aClassNode = ClassHelper.makeWithoutCaching(mapping.a);
        ClassNode bClassNode = ClassHelper.makeWithoutCaching(mapping.b);
        
        def transformerInterfaceClassNode = ClassHelper.makeWithoutCaching(Transformer, false);
        transformerInterfaceClassNode.usingGenerics = true;
        transformerInterfaceClassNode.genericsTypes = [new GenericsType(aClassNode), new GenericsType(bClassNode)];
        mapperClassNode.addInterface(transformerInterfaceClassNode);

        mapperClassNode.addMethod(generateTypeGetter("getSourceType", aClassNode));
        mapperClassNode.addMethod(generateTypeGetter("getTargetType", bClassNode));

        mapperClassNode.addMethod(generateToAMethod(mapperClassNode, mapping, aClassNode, bClassNode));
        
        return mapperClassNode;
    }

    MethodNode generateTypeGetter(String name, ClassNode node) {
        ClassNode nodeClass = ClassHelper.makeWithoutCaching(Class, false);
        nodeClass.usingGenerics = true;
        nodeClass.genericsTypes = [new GenericsType(node)];

        return new MethodNode(name, ACC_PUBLIC, nodeClass, Parameter.EMPTY_ARRAY, null, new ReturnStatement(new ClassExpression(node)));
    }

    MethodNode generateBuildMethod(Set<InnerClassNode> transformers) {
        def resultClassNode = ClassHelper.makeWithoutCaching(HashSet, false);

        def methodBody = new BlockStatement();
        def methodNode = new MethodNode("getMappers", ACC_PUBLIC, ClassHelper.makeWithoutCaching(Collection, false), Parameter.EMPTY_ARRAY, null, methodBody)
        def resultVariable = new VariableExpression("result", resultClassNode)
        def assignToken = new Token(Types.ASSIGNMENT_OPERATOR, "=", -1, -1)

        methodBody.statements << new ExpressionStatement(new DeclarationExpression(resultVariable, assignToken, new ConstructorCallExpression(resultClassNode, new ArgumentListExpression())))

        transformers.each {
            it.enclosingMethod = methodNode;
            methodBody.statements << new ExpressionStatement(new MethodCallExpression(resultVariable, "add", new ConstructorCallExpression(it, new ArgumentListExpression())));
        }

        methodBody.statements << new ReturnStatement(resultVariable);

        return methodNode
    }

    MethodNode generateToAMethod(InnerClassNode mapperClassNode, Mapping mapping, ClassNode aClassNode, ClassNode bClassNode) {
        def methodBody = new BlockStatement();

        def assignToken = new Token(Types.ASSIGNMENT_OPERATOR, "=", -1, -1)
        def resultVariable = new VariableExpression("result", aClassNode)
        def bParameter = new Parameter(bClassNode, "b")
        methodBody.statements << new ExpressionStatement(new DeclarationExpression(resultVariable, assignToken, new ConstructorCallExpression(aClassNode, ArgumentListExpression.EMPTY_ARGUMENTS)))

        aClassNode.fields.each { aField ->
            if((aField.modifiers & ACC_SYNTHETIC) ) {
                return;
            }
            
            Expression value = generateFieldAssign(mapperClassNode, aClassNode, bClassNode, mapping, aField, bParameter);
            
            if(value == null) {
                return;
            }

            methodBody.statements << assign(resultVariable, aField.name, value);
        }

        methodBody.statements << new ReturnStatement(resultVariable)

        return new MethodNode("toA", ACC_PUBLIC, aClassNode, [bParameter] as Parameter[], null, methodBody)
    }
    
    Statement assign(VariableExpression object, String fieldName, Expression value) {
        def propertyExpression = new PropertyExpression(object, fieldName)
        def binaryExpression = new BinaryExpression(propertyExpression, new Token(Types.EQUAL, "=", -1, -1), value)
        return new ExpressionStatement(binaryExpression)
    }
    
    Expression generateFieldAssign(InnerClassNode mapperClassNode, ClassNode aClassNode, ClassNode bClassNode, Mapping mapping, FieldNode aField, Parameter bParameter) {
        Field fieldConfig = mapping.fields?.find { it.aName == aField.name }
        if(fieldConfig?.a != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(fieldConfig.a);

            def methodName = getAFieldConverterName(fieldConfig)
            mapperClassNode.addMethod(methodName, ACC_PUBLIC, aField.type, closure.parameters, null, closure.code);

            return new MethodCallExpression(VariableExpression.THIS_EXPRESSION, methodName, new ArgumentListExpression(bParameter));
        }

        FieldNode bField = bClassNode.getField(fieldConfig?.bName ?: aField.name);
        
        if(bField == null) {
            return null;
        }
        
        def bFieldValue = new PropertyExpression(new VariableExpression(bParameter), bField.name)

        def value = generateFieldValue(aField.type, bField.type, bFieldValue)
        
        if(value == null) {
            return null;
        }

        def notNull = new BooleanExpression(new BinaryExpression(bFieldValue, Token.newSymbol(Types.COMPARE_NOT_EQUAL, -1, -1), ConstantExpression.NULL));
        
        return new TernaryExpression(notNull, value, ConstantExpression.NULL);
    }
    
    Expression generateFieldValue(ClassNode aFieldType, ClassNode bFieldType, PropertyExpression bFieldValue) {
        if(aFieldType.isDerivedFrom(bFieldType)) {
            return bFieldValue;
        }

        def unwrappedAFieldType = ClassHelper.getUnwrapper(aFieldType)

        switch(unwrappedAFieldType) {
            case {it.enum}:
                return generateEnumFieldValue(aFieldType, bFieldType, bFieldValue);
            case {ClassHelper.isPrimitiveType(it)}:
                return new StaticMethodCallExpression(ClassHelper.getWrapper(aFieldType), "valueOf", new ArgumentListExpression(bFieldValue));
            case ClassHelper.STRING_TYPE:
                return new MethodCallExpression(bFieldValue, "toString", ArgumentListExpression.EMPTY_ARGUMENTS);
        }

        //TODO warn about no mapping
        return bFieldValue;
    }
    
    Expression generateEnumFieldValue(ClassNode aFieldType, ClassNode bFieldType, PropertyExpression bFieldValue) {
        Expression enumKey = null;
        switch(bFieldType) {
            case {it.enum}:
                enumKey = new MethodCallExpression(bFieldValue, "name", ArgumentListExpression.EMPTY_ARGUMENTS);
                break;
            case ClassHelper.STRING_TYPE:
                enumKey = bFieldValue;
                break;
        }
        if(enumKey == null) {
            return null;
        }
        return new StaticMethodCallExpression(aFieldType, "valueOf", new ArgumentListExpression(new ClassExpression(aFieldType), enumKey));
    }
    
}
