package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;

import static ru.trylogic.gom.config.dsl.MapperProcessor.*;

@CompileStatic
class EnumConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        if(!targetFieldType.isEnum()) {
            return false;
        }

        switch(sourceFieldValue.type) {
            case {sourceFieldValue.type.enum}:
            case ClassHelper.STRING_TYPE:
                return true;
            default:
                return false;
        }
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        Expression enumKey = null;
        switch(sourceFieldValue.type) {
            case {ClassNode it -> it.enum}:
                enumKey = new MethodCallExpression(sourceFieldValue, "name", EMPTY_ARGUMENTS);
                break;
            case ClassHelper.STRING_TYPE:
                enumKey = sourceFieldValue;
                break;
        }
        if(enumKey == null) {
            return null;
        }
        return nullSafe(sourceFieldValue, new StaticMethodCallExpression(targetFieldType, VALUE_OF, new ArgumentListExpression(new ClassExpression(targetFieldType), enumKey)));
    }
}
