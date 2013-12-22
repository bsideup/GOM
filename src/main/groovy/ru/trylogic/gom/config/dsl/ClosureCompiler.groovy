package ru.trylogic.gom.config.dsl

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.ProcessingUnit
import org.codehaus.groovy.control.SourceUnit

@CompileStatic
class ClosureCompiler implements CompilationUnitAware {

    CompilationUnit compilationUnit;

    ClosureCompiler(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit
    }

    ClosureExpression compile(String script) {
        def scriptClassName = "script" + System.nanoTime()
        def classLoader = new GroovyClassLoader(compilationUnit.classLoader, compilationUnit.configuration);

        CompilationUnit unit = new CompilationUnit(compilationUnit.configuration, null, classLoader);

        unit.setProgressCallback({ ProcessingUnit context, int phase ->
            unit.AST.modules.each { ModuleNode it ->
                it.classes.each { ClassNode classNode ->
                    def progressClassNode = unit.getClassNode(classNode.name)
                    def annotations = progressClassNode?.annotations
                    annotations?.remove(annotations?.find { AnnotationNode annotationNode ->
                        (annotationNode instanceof AnnotationNode) && annotationNode.classNode.name.equalsIgnoreCase(DSLConfigBuilder.name)
                    })
                }
            }
        } as CompilationUnit.ProgressCallback);

        unit.addSource(scriptClassName + ".groovy", script);

        compilationUnit.each { SourceUnit it ->
            GroovyCodeSource src = new GroovyCodeSource(new File(it.name));
            unit.addSource(src.name, src.scriptText);
        }
        unit.compile()

        ClosureExpression result = null;
        unit.AST.modules.each { ModuleNode moduleNode ->
            moduleNode.classes.each { ClassNode classNode ->
                if(classNode.name.equalsIgnoreCase(scriptClassName)) {
                    classNode.methods.each { MethodNode methodNode ->
                        if(methodNode.name.equalsIgnoreCase("run")) {
                            result = ((methodNode.code as BlockStatement).statements.first() as ReturnStatement).expression as ClosureExpression;
                        }
                    }
                }
            }
        }

        return result;
    }
}
