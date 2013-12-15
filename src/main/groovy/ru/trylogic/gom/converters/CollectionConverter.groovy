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
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        
        if(!isIterable(sourceFieldValue.type)) {
            return false;
        }
        
        if(targetFieldType.isInterface()) {
            return getImplementationForInterface(targetFieldType) != null
        } else {
            return isIterable(targetFieldType);
        }
    }
    
    ClassNode getImplementationForInterface(ClassNode interfaceNode) {
        switch(interfaceNode) {
            // Set
            case ClassHelper.makeWithoutCaching(Set, false):
                return ClassHelper.makeWithoutCaching(HashSet, false);
            // Deque
            case ClassHelper.makeWithoutCaching(Deque, false):
                return ClassHelper.makeWithoutCaching(ArrayDeque, false);
            // Another collection
            case ClassHelper.LIST_TYPE:
            case ClassHelper.makeWithoutCaching(Collection, false):
            case ClassHelper.makeWithoutCaching(Iterable, false):
                return ClassHelper.makeWithoutCaching(ArrayList, false);
            default:
                return null;
        }
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        ClassNode resultVariableType = targetFieldType;

        if(targetFieldType.isInterface()) {
            resultVariableType = getImplementationForInterface(targetFieldType);

            resultVariableType.usingGenerics = targetFieldType.usingGenerics;
            resultVariableType.genericsTypes = targetFieldType.genericsTypes;
        }

        def parameterizeSourceFieldType = GenericsUtils.parameterizeType(sourceFieldValue.type, ClassHelper.makeWithoutCaching(Iterable, false));
        def parameterizeTargetFieldType = GenericsUtils.parameterizeType(resultVariableType, ClassHelper.makeWithoutCaching(Iterable, false));

        Parameter sourceParameter = new Parameter(sourceFieldValue.type, '$source')

        def method = mapperClassNode.addMethod("converter" + System.currentTimeMillis(), ACC_PUBLIC, resultVariableType, [sourceParameter] as Parameter[], null, (Statement) macro {
            def $result = $v{new ConstructorCallExpression(resultVariableType, EMPTY_ARGUMENTS)};
            for($item in $v{new VariableExpression(sourceParameter)}) {
                $result.add($v{mapperProcessor.generateFieldValue(mapperClassNode, parameterizeTargetFieldType.genericsTypes[0].type, new VariableExpression('$item', parameterizeSourceFieldType.genericsTypes[0].type))})
            }

            return $result
        });
        return nullSafe(sourceFieldValue, new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue));
    }
}
