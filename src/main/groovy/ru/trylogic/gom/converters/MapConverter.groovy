package ru.trylogic.gom.converters

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;

import static groovyjarjarasm.asm.Opcodes.*;

class MapConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, ClassNode sourceFieldType) {
        if(!(isMap(targetFieldType) || isMap(sourceFieldType))) {
            return false;
        }

        if(sourceFieldType.genericsTypes?.size() != 2) {
            return false;
        }

        return true;
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        ClassNode resultVariableType;
        switch(targetFieldType) {
            case ClassHelper.MAP_TYPE:
                resultVariableType = ClassHelper.makeWithoutCaching(HashMap, false);
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

        def entryType = ClassHelper.makeWithoutCaching(Map.Entry, false);
        entryType.usingGenerics = true;
        entryType.genericsTypes = sourceParameter.type.genericsTypes;
        
        def iParameter = new Parameter(entryType, '$entry');
        
        def keyExpression = new PropertyExpression(new VariableExpression(iParameter), "key");
        keyExpression.type = entryType.genericsTypes.first().type;
        def valueExpression = new PropertyExpression(new VariableExpression(iParameter), "value");
        methodCode.statements << new ForStatement(
                iParameter,
                new MethodCallExpression(new VariableExpression(sourceParameter), "entrySet", EMPTY_ARGUMENTS),
                new ExpressionStatement(new MethodCallExpression(resultVariable, "put", new ArgumentListExpression(
                        mapperProcessor.generateFieldValue(mapperClassNode, resultVariable.originType.genericsTypes[0].type, keyExpression),
                        mapperProcessor.generateFieldValue(mapperClassNode, resultVariable.originType.genericsTypes[1].type, valueExpression)
                )))
        );
        methodCode.statements << new ReturnStatement(resultVariable);

        def method = mapperClassNode.addMethod("converter" + System.currentTimeMillis(), ACC_PUBLIC, resultVariable.originType, [sourceParameter] as Parameter[], null, methodCode);
        return nullSafe(sourceFieldValue, new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue));
    }
}
