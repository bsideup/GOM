package ru.trylogic.gom.config.dsl

import groovy.inspect.swingui.AstNodeToScriptVisitor
import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.ProcessingUnit
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import ru.trylogic.gom.Transformer
import ru.trylogic.gom.config.GOMConfig

import static groovyjarjarasm.asm.Opcodes.*

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DSLConfigBuilderTransformation implements ASTTransformation, CompilationUnitAware {

    CompilationUnit compilationUnit;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        
        ClassNode classNode = nodes.find { it instanceof ClassNode } as ClassNode;

        def config = new DSLExecutor(compilationUnit).execute(classNode, source);
        
        new MapperProcessor(compilationUnit).process(classNode, config);

        StringWriter writer = new StringWriter()
        classNode.module.classes.each(new AstNodeToScriptVisitor(writer).&visitClass)
        println writer
    }
}


