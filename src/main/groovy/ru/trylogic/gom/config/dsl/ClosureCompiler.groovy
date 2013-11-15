package ru.trylogic.gom.config.dsl

import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.ProcessingUnit

class ClosureCompiler implements CompilationUnitAware {

    CompilationUnit compilationUnit;

    ClosureCompiler(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit
    }

    ClosureExpression compile(String script) {
        def scriptClassName = "script" + System.currentTimeMillis()
        def classLoader = new GroovyClassLoader(compilationUnit.classLoader, compilationUnit.configuration);

        CompilationUnit unit = new CompilationUnit(compilationUnit.configuration, null, classLoader);

        unit.setProgressCallback({ ProcessingUnit context, int phase ->
            unit.AST.modules.each {
                it.classes.each {
                    def progressClassNode = unit.getClassNode(it.name)
                    def annotations = progressClassNode?.annotations
                    annotations?.remove(annotations?.find { (it instanceof AnnotationNode) && it.classNode.name.equalsIgnoreCase(DSLConfigBuilder.name) })
                }
            }
        } as CompilationUnit.ProgressCallback);

        unit.addSource(scriptClassName + ".groovy", script);

        compilationUnit.sources.each {
            GroovyCodeSource src = new GroovyCodeSource(new File(it.value.name));
            unit.addSource(src.name, src.scriptText);
        }
        unit.compile()

        ClosureExpression result = null;
        unit.AST.modules.each {
            it.classes.each {
                if(it.name.equalsIgnoreCase(scriptClassName)) {
                    it.methods.each {
                        if(it.name.equalsIgnoreCase("run")) {
                            result = ((it.code as BlockStatement).statements.first() as ReturnStatement).expression as ClosureExpression;
                        }
                    }
                }
            }
        }

        return result;
    }
}
