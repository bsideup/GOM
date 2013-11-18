package ru.trylogic.gom.config.dsl

import groovy.inspect.swingui.AstNodeToScriptVisitor
import groovy.json.JsonBuilder
import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.*
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.Parameter.EMPTY_ARRAY;

import static groovyjarjarasm.asm.Opcodes.*

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DSLConfigBuilderTransformation implements ASTTransformation, CompilationUnitAware {

    CompilationUnit compilationUnit;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        
        ClassNode classNode = nodes.find { it instanceof ClassNode } as ClassNode;

        classNode.superClass = ClassHelper.makeWithoutCaching(DSLConfigBuilderBase, false);

        classNode.modifiers |= ACC_FINAL;

        def config = new DSLExecutor(compilationUnit).execute(classNode, source);
        
        def transformers = new MapperProcessor(compilationUnit).process(classNode, config);

        transformers.each(classNode.module.&addClass);

        classNode.addMethod(generateBuildMethod(transformers));

        StringWriter writer = new StringWriter()
        classNode.module.classes.each(new AstNodeToScriptVisitor(writer).&visitClass)
        println writer
    }

    MethodNode generateBuildMethod(Set<InnerClassNode> transformers) {
        def resultClassNode = ClassHelper.makeWithoutCaching(HashSet, false);

        def methodBody = new BlockStatement();
        def methodNode = new MethodNode("getMappers", ACC_PUBLIC, ClassHelper.makeWithoutCaching(Collection, false), EMPTY_ARRAY, null, methodBody)
        def resultVariable = new VariableExpression('$result', resultClassNode)

        methodBody.statements << declStatement(resultVariable, new ConstructorCallExpression(resultClassNode, EMPTY_ARGUMENTS))

        transformers.each {
            it.enclosingMethod = methodNode;
            methodBody.statements << new ExpressionStatement(new MethodCallExpression(resultVariable, "add", new ConstructorCallExpression(it, EMPTY_ARGUMENTS)));
        }

        methodBody.statements << new ReturnStatement(resultVariable);

        return methodNode
    }
}


