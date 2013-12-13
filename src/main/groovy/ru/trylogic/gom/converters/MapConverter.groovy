package ru.trylogic.gom.converters

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;

import static groovyjarjarasm.asm.Opcodes.*;

class MapConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, ClassNode sourceFieldType) {
        if(!(isOrImplements(targetFieldType, ClassHelper.MAP_TYPE))) {
            return false;
        }

        if(!isOrImplements(sourceFieldType, ClassHelper.MAP_TYPE)) {
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
                break;
            default:
                resultVariableType = targetFieldType;
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
        def key = mapperProcessor.generateFieldValue(mapperClassNode, resultVariable.originType.genericsTypes[0].type, keyExpression)

        def valueExpression = new PropertyExpression(new VariableExpression(iParameter), "value");
        def value = mapperProcessor.generateFieldValue(mapperClassNode, resultVariable.originType.genericsTypes[1].type, valueExpression)


        def forBody = new ExpressionStatement(new MethodCallExpression(resultVariable, "put", new ArgumentListExpression(key, value)))
        methodCode.statements << new ForStatement(iParameter, new MethodCallExpression(new VariableExpression(sourceParameter), "entrySet", EMPTY_ARGUMENTS), new BlockStatement([forBody], new VariableScope()));
        methodCode.statements << new ReturnStatement(resultVariable);

        def method = mapperClassNode.addMethod("converter" + System.currentTimeMillis(), ACC_PUBLIC, resultVariable.originType, [sourceParameter] as Parameter[], null, methodCode);
        return nullSafe(sourceFieldValue, new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue));
    }
}
