package ru.trylogic.gom.config.dsl

import groovy.inspect.swingui.AstNodeToScriptVisitor
import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.ProcessingUnit
import org.codehaus.groovy.control.SourceUnit
import ru.trylogic.gom.config.GOMConfig

class DSLExecutor implements CompilationUnitAware {

    CompilationUnit compilationUnit;

    DSLExecutor(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit
    }

    GOMConfig execute(ClassNode classNode, SourceUnit source) {
        def gcl = new GroovyClassLoader(compilationUnit.classLoader, compilationUnit.configuration);

        CompilationUnit unit = new CompilationUnit(compilationUnit.configuration, null, gcl);

        SourceUnit su = null;
        compilationUnit.sources.each {
            GroovyCodeSource src = new GroovyCodeSource(new File(it.value.name));
            def sourceUnit = unit.addSource(src.name, src.scriptText);
            if(it.value.name.equalsIgnoreCase(source.name)) {
                su = sourceUnit;
            }
        }

        GroovyClassLoader.ClassCollector collector = gcl.createCollector(unit, su);

        unit.setProgressCallback({ ProcessingUnit context, int phase ->
            def progressClassNode = unit.getClassNode(classNode.name)
            def annotations = progressClassNode?.annotations
            annotations?.remove(annotations?.find { (it instanceof AnnotationNode) && it.classNode.name.equalsIgnoreCase(DSLConfigBuilder.name) })

            if(phase != Phases.SEMANTIC_ANALYSIS) {
                return;
            }

            progressClassNode?.getField("mappers")?.initialExpression?.visit(new CodeVisitorSupport() {
                @Override
                void visitMethodCallExpression(MethodCallExpression call) {
                    super.visitMethodCallExpression(call)
                    if(!call.methodAsString.equalsIgnoreCase("a") && !call.methodAsString.equalsIgnoreCase("b") ) {
                        return;
                    }

                    def argumentsExpressions = (call.arguments as ArgumentListExpression)?.expressions

                    ClosureExpression cl = argumentsExpressions.get(0) as ClosureExpression;

                    if(cl == null) {
                        return;
                    }

                    StringWriter writer = new StringWriter()
                    new AstNodeToScriptVisitor(writer).visitClosureExpression(cl);

                    argumentsExpressions.clear();
                    argumentsExpressions.add(new ConstantExpression(writer.toString()));
                }
            })

        } as CompilationUnit.ProgressCallback)

        unit.setClassgenCallback(collector);
        unit.compile();

        Class answer = collector.generatedClass;
        String mainClass = su.getAST().getMainClassName();
        for (Class clazz : collector.getLoadedClasses()) {
            String clazzName = clazz.getName();
            gcl.definePackage(clazzName);
            gcl.setClassCacheEntry(clazz);
            if (clazzName.equals(mainClass)) {
                answer = clazz
            }
        }

        GOMConfig config = null;
        try {
            config = (answer.newInstance() as DSLConfigBuilderBase).build()
        } catch(Exception e) {
            e.printStackTrace()
        }
        
        return config;
    }
    
}
