package ru.trylogic.gom.converters

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.transform.AbstractASTTransformUtil

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;

import static groovyjarjarasm.asm.Opcodes.*;

class CollectionConverter extends AbstractConverter {

    @Override
    boolean match(ClassNode targetFieldType, ClassNode sourceFieldType) {
        
        if(!(isIterable(sourceFieldType))) {
            return false;
        }

        if(sourceFieldType.genericsTypes?.size() != 1) {
            return false;
        }
        
        return isList(targetFieldType) || isSet(targetFieldType);
    }

    boolean isList(ClassNode classNode) {
        return classNode == ClassHelper.LIST_TYPE;
    }

    boolean isSet(ClassNode classNode) {
        return classNode == ClassHelper.makeWithoutCaching(Set, false);
    }

    boolean isIterable(ClassNode classNode) {
        return isOrImplements(classNode, ClassHelper.makeWithoutCaching(Iterable, false));
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        ClassNode resultVariableType;
        switch(targetFieldType) {
            case ClassHelper.LIST_TYPE:
                resultVariableType = ClassHelper.makeWithoutCaching(ArrayList, false);
                break;
            case ClassHelper.makeWithoutCaching(Set, false):
                resultVariableType = ClassHelper.makeWithoutCaching(HashSet, false);
                break;
            default:
                resultVariableType = targetFieldType.getPlainNodeReference();
        }

        resultVariableType.usingGenerics = true;
        resultVariableType.genericsTypes = targetFieldType.genericsTypes

        def resultVariable = new VariableExpression('$result', resultVariableType)

        Parameter sourceParameter = new Parameter(sourceFieldValue.type, '$source')

        def methodCode = new BlockStatement();
        methodCode.statements << declStatement(resultVariable, new ConstructorCallExpression(resultVariable.originType, EMPTY_ARGUMENTS));

        def loopBlock = new BlockStatement();

        def iParameter = new Parameter(sourceParameter.type.genericsTypes.first().type, '$item');

        def value = mapperProcessor.generateFieldValue(mapperClassNode, resultVariable.originType.genericsTypes.first().type, new VariableExpression(iParameter))

        loopBlock.statements << new ExpressionStatement(new MethodCallExpression(resultVariable, "add", value));
        methodCode.statements << new ForStatement(iParameter, new VariableExpression(sourceParameter), loopBlock);
        methodCode.statements << new ReturnStatement(resultVariable);

        def method = mapperClassNode.addMethod("converter" + System.currentTimeMillis(), ACC_PUBLIC, resultVariable.originType, [sourceParameter] as Parameter[], null, methodCode);
        return nullSafe(sourceFieldValue, new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue));
    }
}
