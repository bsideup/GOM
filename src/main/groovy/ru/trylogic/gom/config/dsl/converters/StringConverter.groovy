package ru.trylogic.gom.config.dsl.converters

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static ru.trylogic.gom.config.dsl.MapperProcessor.*;

class StringConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, ClassNode sourceFieldType) {
        return targetFieldType == ClassHelper.STRING_TYPE;
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        return nullSafe(sourceFieldValue, new MethodCallExpression(sourceFieldValue, TO_STRING, EMPTY_ARGUMENTS));
    }
}
