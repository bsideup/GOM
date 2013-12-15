package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.ast.tools.GenericsUtils

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;

import static groovyjarjarasm.asm.Opcodes.*;

@CompileStatic
class CollectionConverter extends AbstractConverter {

    @Override
    boolean match(ClassNode targetFieldType, ClassNode sourceFieldType) {
        return isIterable(targetFieldType) && isIterable(sourceFieldType);
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        ClassNode resultVariableType;
        switch(targetFieldType) {
            case ClassHelper.makeWithoutCaching(Set, false):
                resultVariableType = ClassHelper.makeWithoutCaching(HashSet, false);
                break;
            case ClassHelper.makeWithoutCaching(Deque, false):
                resultVariableType = ClassHelper.makeWithoutCaching(ArrayDeque, false);
                break;
            case {ClassNode it -> it.isInterface()}:
                resultVariableType = ClassHelper.makeWithoutCaching(ArrayList, false);
                break;
            default:
                resultVariableType = targetFieldType.getPlainNodeReference();
        }

        resultVariableType.usingGenerics = targetFieldType.usingGenerics;
        resultVariableType.genericsTypes = targetFieldType.genericsTypes

        def parameterizeSourceFieldType = GenericsUtils.parameterizeType(sourceFieldValue.type, ClassHelper.makeWithoutCaching(Iterable, false));
        def parameterizeTargetFieldType = GenericsUtils.parameterizeType(resultVariableType, ClassHelper.makeWithoutCaching(Iterable, false));

        def resultVariable = new VariableExpression('$result', resultVariableType)
        
        Parameter sourceParameter = new Parameter(sourceFieldValue.type, '$source')

        def methodCode = new BlockStatement();
        methodCode.statements << declStatement(resultVariable, new ConstructorCallExpression(resultVariable.originType, EMPTY_ARGUMENTS));

        def iParameter = new Parameter(parameterizeSourceFieldType.genericsTypes[0].type, '$item');

        def value = mapperProcessor.generateFieldValue(mapperClassNode, parameterizeTargetFieldType.genericsTypes[0].type, new VariableExpression(iParameter))

        methodCode.statements << new ForStatement(
                iParameter,
                new VariableExpression(sourceParameter),
                new ExpressionStatement(new MethodCallExpression(resultVariable, "add", value))
        );
        methodCode.statements << new ReturnStatement(resultVariable);

        def method = mapperClassNode.addMethod("converter" + System.currentTimeMillis(), ACC_PUBLIC, resultVariable.originType, [sourceParameter] as Parameter[], null, methodCode);
        return nullSafe(sourceFieldValue, new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue));
    }
}
