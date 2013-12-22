package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.dsl.ClosureCompiler

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;

import static groovyjarjarasm.asm.Opcodes.*;

@CompileStatic
class AnyObjectConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        
        if(targetFieldType.isInterface()) {
            return false;
        }
        
        if(targetFieldType == ClassHelper.OBJECT_TYPE) {
            return false;
        }
        
        return true
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue, GOMConfig.Direction direction) {
        def sourceFieldType = sourceFieldValue.type
        Parameter sourceParameter = new Parameter(sourceFieldType, '$source')

        def mapping = config.mappings.find {GOMConfig.Mapping it ->
            it.a.name == direction.ab(targetFieldType, sourceFieldType).name && it.b.name == direction.ab(sourceFieldType, targetFieldType).name
        }
        String toMethodCode = mapping != null ? direction.toMethodCode(mapping) : null

        def method;
        if(toMethodCode != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(toMethodCode);
            method = mapperClassNode.addMethod(generateMethodName(mapperClassNode), ACC_PUBLIC, targetFieldType, [new Parameter(sourceFieldType, mapping.inverted ? direction.parameterName : direction.opositeParameterName)] as Parameter[], null, closure.code);
        } else {
            Statement converterMethodCode = generateConverter(mapping, mapperClassNode, direction, targetFieldType, sourceFieldType);
            method = mapperClassNode.addMethod(generateMethodName(mapperClassNode), ACC_PUBLIC, targetFieldType, [sourceParameter] as Parameter[], null, converterMethodCode);
        }
        
        return nullSafe(sourceFieldValue, new MethodCallExpression(THIS_EXPRESSION, method.name, sourceFieldValue));
    }
    
    Statement generateConverter(GOMConfig.Mapping mapping, InnerClassNode mapperClassNode, GOMConfig.Direction direction, ClassNode targetFieldType, ClassNode sourceFieldType) {
        Parameter sourceParameter = new Parameter(sourceFieldType, '$source')
        def methodBody = new BlockStatement();

        methodBody.statements << (Statement) macro {
            if($v{new VariableExpression(sourceParameter)} == null) {
                return null;
            }
        }

        def targetVariable = new VariableExpression('$result', targetFieldType)
        methodBody.statements << declStatement(targetVariable, new ConstructorCallExpression(targetFieldType, EMPTY_ARGUMENTS));

        targetFieldType.fields.each { FieldNode targetField ->
            if((targetField.modifiers & ACC_SYNTHETIC) ) {
                return;
            }

            Expression value = generateFieldAssign(mapping, direction, mapperClassNode, sourceFieldType, targetField, sourceParameter);

            if(value == null) {
                return;
            }

            def propertyExpression = new PropertyExpression(targetVariable, targetField.name)
            methodBody.statements << assignStatement(propertyExpression, value);
        }

        methodBody.statements << (Statement) macro {return $v{targetVariable}}
        
        return methodBody;
    }

    Expression generateFieldAssign(GOMConfig.Mapping mapping, GOMConfig.Direction direction, InnerClassNode mapperClassNode, ClassNode sourceClassNode, FieldNode targetField, Parameter sourceParameter) {
        GOMConfig.Field fieldConfig = mapping?.fields?.find { GOMConfig.Field it -> direction.getSourceFieldName(it) == targetField.name }
        String sourceFieldConverterCode;
        if(fieldConfig != null) {
            sourceFieldConverterCode = direction.getFieldConverterCode(fieldConfig)
        }
        if(sourceFieldConverterCode != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(sourceFieldConverterCode);
            def method = mapperClassNode.addMethod(generateMethodName(mapperClassNode), ACC_PUBLIC, targetField.type, [new Parameter(sourceClassNode, mapping.inverted ? direction.parameterName : direction.opositeParameterName)] as Parameter[], null, closure.code);
            return new MethodCallExpression(THIS_EXPRESSION, method.name, new ArgumentListExpression(sourceParameter));
        }

        FieldNode sourceField = sourceClassNode.getField(direction.getTargetFieldName(fieldConfig) ?: targetField.name);

        if(sourceField == null) {
            return null;
        }

        def sourceFieldValue = new PropertyExpression(new VariableExpression(sourceParameter), sourceField.name)
        sourceFieldValue.type = sourceClassNode.getField(sourceField.name).type

        return mapperProcessor.generateFieldValue(direction, mapperClassNode, targetField.type, sourceFieldValue)
    }
}
