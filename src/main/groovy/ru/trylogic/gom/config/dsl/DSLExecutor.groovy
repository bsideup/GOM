package ru.trylogic.gom.config.dsl

import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
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

            progressClassNode?.objectInitializerStatements*.visit(new DslPreparator());

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
