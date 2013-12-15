package ru.trylogic.gom.config.dsl

import groovy.inspect.swingui.AstNodeToScriptVisitor
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
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
import ru.trylogic.gom.config.GOMConfig

import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION

@CompileStatic
class DslPreparator extends CodeVisitorSupport {

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

                if(closure.parameters[0].name != GOMConfig.Direction.A.parameterName) {
                    throw new Exception("Mapping closure first parameter name shold be '${GOMConfig.Direction.A.parameterName}'");
                }

                if(closure.parameters[1].name != GOMConfig.Direction.B.parameterName) {
                    throw new Exception("Mapping closure second parameter name shold be '${GOMConfig.Direction.B.parameterName}'");
                }

                def newArguments = new ArgumentListExpression();

                newArguments.expressions << new ClassExpression(closure.parameters[0].originType)
                newArguments.expressions << new ClassExpression(closure.parameters[1].originType)
                newArguments.expressions << closure

                call.method = new ConstantExpression("doMapping");
                call.arguments = newArguments;

                closure.parameters*.initialExpression = (Expression) new ConstantExpression(null);

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

                ClosureExpression closure = null;
                if(arguments.expressions.last() instanceof ClosureExpression) {
                    closure = arguments.expressions.pop() as ClosureExpression;
                }

                arguments.expressions.eachWithIndex { Expression expression, int index ->
                    if(!(expression instanceof PropertyExpression)) {
                        throw new Exception("Field argument should be PropertyExpression");
                    }

                    def prop = expression as PropertyExpression;

                    if(!(prop.objectExpression instanceof VariableExpression)) {
                        throw new Exception("Field argument property source should be one of mapping arguments");
                    }

                    def var = prop.objectExpression as VariableExpression;

                    if(arguments.expressions.size() == 2) {
                        if(var.name != GOMConfig.Direction.values()[index].parameterName) {
                            throw new Exception("Field arguments should have order of mapping arguments")
                        }
                    } else {
                        if(!GOMConfig.Direction.values().any {GOMConfig.Direction direction -> direction.parameterName == var.name}) {
                            throw new Exception("Field argument should reference mapping argument");
                        }
                    }

                    newArguments.expressions << prop.property;
                }
                if(closure != null) {
                    newArguments.expressions << closure;
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

                def resultClosure = new ClosureExpression([new Parameter(ClassHelper.OBJECT_TYPE, call.methodAsString == "toA" ? "b" : "a")] as Parameter[], cl.code);
                
                argumentsExpressions.set(0, resultClosure);
                
                StringWriter writer = new StringWriter()
                new AstNodeToScriptVisitor(writer).visitClosureExpression(resultClosure);

                call.arguments = new ConstantExpression(writer.toString())
                break;
            default:
                return;
        }
    }
}