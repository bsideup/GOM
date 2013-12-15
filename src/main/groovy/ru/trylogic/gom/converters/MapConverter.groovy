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
class MapConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        
        if(!isMap(sourceFieldValue.type)) {
            return false;
        }
        
        if(targetFieldType.isInterface()) {
            return getImplementationForInterface(targetFieldType) != null;
        } else {
            return isMap(targetFieldType)
        }
    }

    ClassNode getImplementationForInterface(ClassNode interfaceNode) {
        switch(interfaceNode) {
            case ClassHelper.MAP_TYPE:
                return ClassHelper.makeWithoutCaching(HashMap, false);
            default:
                return null;
        }
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        ClassNode resultVariableType = targetFieldType;
        
        if(resultVariableType.isInterface()) {
            resultVariableType = getImplementationForInterface(resultVariableType);

            resultVariableType.usingGenerics = targetFieldType.usingGenerics;
            resultVariableType.genericsTypes = targetFieldType.genericsTypes;
        }

        def parameterizeSourceFieldType = GenericsUtils.parameterizeType(sourceFieldValue.type, ClassHelper.makeWithoutCaching(Map, false));
        def parameterizeTargetFieldType = GenericsUtils.parameterizeType(resultVariableType, ClassHelper.makeWithoutCaching(Map, false));

        def resultVariable = new VariableExpression('$result', resultVariableType)

        Parameter sourceParameter = new Parameter(sourceFieldValue.type, '$source')

        def methodCode = new BlockStatement();
        methodCode.statements << declStatement(resultVariable, new ConstructorCallExpression(resultVariable.originType, EMPTY_ARGUMENTS));

        def entryType = ClassHelper.makeWithoutCaching(Map.Entry, false);
        entryType.usingGenerics = true;
        entryType.genericsTypes = parameterizeSourceFieldType.genericsTypes;
        
        def iParameter = new Parameter(entryType, '$entry');
        
        def keyExpression = new PropertyExpression(new VariableExpression(iParameter), "key");
        keyExpression.type = entryType.genericsTypes.first().type;
        def valueExpression = new PropertyExpression(new VariableExpression(iParameter), "value");
        methodCode.statements << new ForStatement(
                iParameter,
                new MethodCallExpression(new VariableExpression(sourceParameter), "entrySet", EMPTY_ARGUMENTS),
                new ExpressionStatement(new MethodCallExpression(resultVariable, "put", new ArgumentListExpression(
                        mapperProcessor.generateFieldValue(mapperClassNode, parameterizeTargetFieldType.genericsTypes[0].type, keyExpression),
                        mapperProcessor.generateFieldValue(mapperClassNode, parameterizeTargetFieldType.genericsTypes[1].type, valueExpression)
                )))
        );
        methodCode.statements << new ReturnStatement(resultVariable);

        def method = mapperClassNode.addMethod("converter" + System.currentTimeMillis(), ACC_PUBLIC, resultVariable.originType, [sourceParameter] as Parameter[], null, methodCode);
        return nullSafe(sourceFieldValue, new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue));
    }
}
