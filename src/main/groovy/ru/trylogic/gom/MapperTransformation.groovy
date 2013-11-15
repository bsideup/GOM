package ru.trylogic.gom

import groovy.inspect.swingui.AstNodeToScriptVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class MapperTransformation implements ASTTransformation {
    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        AnnotationNode anno = nodes.find { it instanceof AnnotationNode } as AnnotationNode

        ClassNode aClassNode = (anno.getMember("to") as ClassExpression).type;
        ClassNode bClassNode = (anno.getMember("from") as ClassExpression).type;

        ClassNode transformerClassNode = nodes.find { it instanceof ClassNode } as ClassNode;

        def transformerInterfaceClassNode = ClassHelper.makeWithoutCaching(Transformer, false);
        transformerInterfaceClassNode.usingGenerics = true;
        transformerInterfaceClassNode.genericsTypes = [new GenericsType(aClassNode), new GenericsType(bClassNode)];
        transformerClassNode.addInterface(transformerInterfaceClassNode);

        if (!transformerClassNode.methods.any { it.name == "transform" }) {
            transformerClassNode.addMethod(generateTransformMethod(transformerClassNode, aClassNode, bClassNode));
        }

        //transformerClassNode.addMethod(generateTypeGetter("getSourceType", aClassNode));
        //transformerClassNode.addMethod(generateTypeGetter("getTargetType", bClassNode));

        StringWriter writer = new StringWriter()
        new AstNodeToScriptVisitor(writer).visitClass(transformerClassNode)
        ///println writer
    }

    def MethodNode generateTransformMethod(ClassNode transformerClassNode, ClassNode aClassNode, ClassNode bClassNode) {
        def methodBody = new BlockStatement();

        def assignToken = new Token(Types.ASSIGNMENT_OPERATOR, "=", -1, -1)
        def resultVariable = new VariableExpression("result", aClassNode)
        def bVariable = new VariableExpression("from")
        def thisVariable = new VariableExpression("this")

        methodBody.statements << new ExpressionStatement(new DeclarationExpression(resultVariable, assignToken, new ConstructorCallExpression(aClassNode, new ArgumentListExpression())))

        aClassNode.fields.each { field ->
            def convertMethod = transformerClassNode.methods.find { it.name == field.name }

            Expression value = null;
            if (convertMethod) {
                value = new MethodCallExpression(thisVariable, field.name, new ArgumentListExpression(bVariable))
            } else {
                def overridenField = transformerClassNode.fields.find { it.name == field.name }
                
                if(overridenField) {
                    value = new MethodCallExpression(new VariableExpression(field.name), "transform", new ArgumentListExpression(new PropertyExpression(bVariable, field.name)))
                }
                else if (bClassNode.fields.any { it.name == field.name }) {
                    value = new PropertyExpression(bVariable, field.name);
                }
            }

            if (value != null) {
                def propertyExpression = new PropertyExpression(resultVariable, field.name)
                def binaryExpression = new BinaryExpression(propertyExpression, new Token(Types.EQUAL, "=", -1, -1), value)
                methodBody.statements << new ExpressionStatement(binaryExpression)
            }
        }

        methodBody.statements << new ReturnStatement(resultVariable)

        return new MethodNode("transform", Opcodes.ACC_PUBLIC, aClassNode, [new Parameter(bClassNode, bVariable.name)] as Parameter[], null, methodBody)
    }

    def MethodNode generateTypeGetter(String name, ClassNode node) {
        ClassNode nodeClass = ClassHelper.makeWithoutCaching(Class, false);
        nodeClass.usingGenerics = true;
        nodeClass.genericsTypes = [new GenericsType(node)];

        return new MethodNode(name, Opcodes.ACC_PUBLIC, nodeClass, [] as Parameter[], null, new ReturnStatement(new ClassExpression(node)));
    }
}