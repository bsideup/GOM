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

        def keyExpression = (Expression) macro {$entry.key}
        keyExpression.type = parameterizeSourceFieldType.genericsTypes.first().type;

        def method = mapperClassNode.addMethod("converter" + System.currentTimeMillis(), ACC_PUBLIC, resultVariableType, [new Parameter(sourceFieldValue.type, '$source')] as Parameter[], null, (Statement) macro {
            def $result = $v{new ConstructorCallExpression(resultVariableType, EMPTY_ARGUMENTS)};

            for($entry in $source.entrySet()) {
                $result.put(
                        $v{mapperProcessor.generateFieldValue(mapperClassNode, parameterizeTargetFieldType.genericsTypes[0].type, keyExpression)},
                        $v{mapperProcessor.generateFieldValue(mapperClassNode, parameterizeTargetFieldType.genericsTypes[1].type, (Expression) macro {$entry.value})}
                )
            }
            return $result;
        });
        return nullSafe(sourceFieldValue, new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue));
    }
}
