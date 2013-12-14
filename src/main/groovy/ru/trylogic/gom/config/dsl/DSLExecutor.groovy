package ru.trylogic.gom.config.dsl

import groovy.inspect.swingui.AstNodeToScriptVisitor
import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.ProcessingUnit
import org.codehaus.groovy.control.SourceUnit
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.GOMConfig.Direction

import static org.codehaus.groovy.ast.expr.VariableExpression.*;

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

            progressClassNode?.objectInitializerStatements*.visit(new CodeVisitorSupport() {
                @Override
                void visitMethodCallExpression(MethodCallExpression call) {
                    super.visitMethodCallExpression(call)
                    
                    if(call.objectExpression != THIS_EXPRESSION) {
                        return;
                    }

                    switch(call.methodAsString) {
                        case "mapping":
                            ArgumentListExpression arguments = call.arguments as ArgumentListExpression;
                        
                            def closure = arguments.expressions.get(0) as ClosureExpression;
                            
                            if(closure.parameters.size() != 2) {
                                throw new Exception("Mapping closure should have 2 parameters!");
                            }

                            if(closure.parameters[0].name != Direction.A.parameterName) {
                                throw new Exception("Mapping closure first parameter name shold be '${Direction.A.parameterName}'");
                            }

                            if(closure.parameters[1].name != Direction.B.parameterName) {
                                throw new Exception("Mapping closure second parameter name shold be '${Direction.b.parameterName}'");
                            }
                        
                            def newArguments = new ArgumentListExpression();

                            newArguments.expressions << new ClassExpression(closure.parameters[0].originType)
                            newArguments.expressions << new ClassExpression(closure.parameters[1].originType)
                            newArguments.expressions << closure
                        
                            call.method = new ConstantExpression("doMapping");
                            call.arguments = newArguments;
                        
                            closure.parameters*.initialExpression = new ConstantExpression(null);
                        
                            break;
                        case "field":
                            def arguments = call.arguments as ArgumentListExpression;
                            
                            if(arguments == null) {
                                throw new Exception("Field should have arguments");
                            }
                        
                            if(arguments.expressions.size() != 1 && arguments.expressions.size() != 2 ) {
                                throw new Exception("Field arguments size should be 1 or 2");
                            }
                        
                            ArgumentListExpression newArguments = new ArgumentListExpression();
                            
                            def closure = null;
                            if(arguments.expressions.last() instanceof ClosureExpression) {
                                closure = arguments.expressions.pop();
                            }
                            
                            arguments.expressions.eachWithIndex { Expression it, int index ->
                                if(!(it instanceof PropertyExpression)) {
                                    throw new Exception("Field argument should be PropertyExpression");
                                }
                                
                                def prop = it as PropertyExpression;
                                
                                if(!(prop.objectExpression instanceof VariableExpression)) {
                                    throw new Exception("Field argument property source should be one of mapping arguments");
                                }
                                
                                def var = prop.objectExpression as VariableExpression;
                                
                                if(arguments.expressions.size() == 2) {
                                    if(var.name != Direction.values()[index].parameterName) {
                                        throw new Exception("Field arguments should have order of mapping arguments")
                                    }
                                } else {
                                    if(!Direction.values().any {it.parameterName == var.name}) {
                                        throw new Exception("Field argument should reference mapping argument");
                                    }
                                }

                                newArguments.expressions.add(prop.property);
                            }
                            if(closure != null) {
                                newArguments.expressions.add(closure);
                            }
                            call.arguments = newArguments;
                            break;
                        case "toA":
                        case "toB":
                            def argumentsExpressions = (call.arguments as ArgumentListExpression)?.expressions
                            
                            if(argumentsExpressions == null) {
                                break;
                            }
                            
                            if(argumentsExpressions.size() == 0) {
                                break;
                            }

                            ClosureExpression cl = argumentsExpressions.get(0) as ClosureExpression;

                            if(cl == null) {
                                break;
                            }

                            StringWriter writer = new StringWriter()
                            new AstNodeToScriptVisitor(writer).visitClosureExpression(cl);
                        
                            call.arguments = new ConstantExpression(writer.toString())
                            break;
                        default:
                            return;
                    }
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
