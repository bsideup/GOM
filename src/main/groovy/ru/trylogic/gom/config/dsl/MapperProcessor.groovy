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

            if (!classNode.module.classes.any { it.name.equalsIgnoreCase(className) }) {
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

        mapperClassNode.addMethod(generateToAMethod(mapperClassNode, mapping, aClassNode, bClassNode));

        mapperClassNode.addMethod(generateTypeGetter("getSourceType", aClassNode));
        mapperClassNode.addMethod(generateTypeGetter("getTargetType", bClassNode));
        
        return mapperClassNode;
    }

    def MethodNode generateBuildMethod(Set<InnerClassNode> transformers) {
        def resultClassNode = ClassHelper.makeWithoutCaching(HashSet, false);

        def methodBody = new BlockStatement();
        def methodNode = new MethodNode("getMappers", ACC_PUBLIC, ClassHelper.makeWithoutCaching(Collection, false), [] as Parameter[], null, methodBody)
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

    def MethodNode generateToAMethod(InnerClassNode mapperClassNode, Mapping mapping, ClassNode aClassNode, ClassNode bClassNode) {
        def methodBody = new BlockStatement();

        def assignToken = new Token(Types.ASSIGNMENT_OPERATOR, "=", -1, -1)
        def resultVariable = new VariableExpression("result", aClassNode)
        def bVariable = new VariableExpression("b")
        def thisVariable = new VariableExpression("this")

        methodBody.statements << new ExpressionStatement(new DeclarationExpression(resultVariable, assignToken, new ConstructorCallExpression(aClassNode, new ArgumentListExpression())))

        aClassNode.fields.each { field ->
            
            if((field.modifiers & ACC_SYNTHETIC) ) {
                return;
            }
            
            Field fieldConfig = mapping.fields?.find { it.aName == field.name }

            Expression value;
            if(fieldConfig != null) {
                if(fieldConfig.a != null) {
                    def closure = new ClosureCompiler(compilationUnit).compile(fieldConfig.a);

                    def methodName = getAFieldConverterName(fieldConfig)
                    mapperClassNode.addMethod(methodName, ACC_PUBLIC, field.type, closure.parameters, null, closure.code);
                    
                    value = new MethodCallExpression(thisVariable, methodName, new ArgumentListExpression(bVariable));
                } else {
                    value = new PropertyExpression(bVariable, fieldConfig.bName)
                }
            } else if(bClassNode.fields.any { it.name.equalsIgnoreCase(field.name) }) {
                value = new PropertyExpression(bVariable, field.name);
            } else {
                return;
            }

            def propertyExpression = new PropertyExpression(resultVariable, field.name)
            def binaryExpression = new BinaryExpression(propertyExpression, new Token(Types.EQUAL, "=", -1, -1), value)
            methodBody.statements << new ExpressionStatement(binaryExpression)
        }

        methodBody.statements << new ReturnStatement(resultVariable)

        return new MethodNode("toA", ACC_PUBLIC, aClassNode, [new Parameter(bClassNode, bVariable.name)] as Parameter[], null, methodBody)
    }

    def MethodNode generateTypeGetter(String name, ClassNode node) {
        ClassNode nodeClass = ClassHelper.makeWithoutCaching(Class, false);
        nodeClass.usingGenerics = true;
        nodeClass.genericsTypes = [new GenericsType(node)];

        return new MethodNode(name, ACC_PUBLIC, nodeClass, [] as Parameter[], null, new ReturnStatement(new ClassExpression(node)));
    }
    
    
}
