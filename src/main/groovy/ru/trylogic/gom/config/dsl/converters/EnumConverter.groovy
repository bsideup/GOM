package ru.trylogic.gom.config.dsl.converters

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;

import static ru.trylogic.gom.config.dsl.MapperProcessor.*;

class EnumConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, ClassNode sourceFieldType) {
        return targetFieldType.isEnum();
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        Expression enumKey = null;
        switch(sourceFieldValue.type) {
            case {it.enum}:
                enumKey = new MethodCallExpression(sourceFieldValue, "name", EMPTY_ARGUMENTS);
                break;
            case ClassHelper.STRING_TYPE:
                enumKey = sourceFieldValue;
                break;
        }
        if(enumKey == null) {
            return null;
        }
        return new StaticMethodCallExpression(targetFieldType, VALUE_OF, new ArgumentListExpression(new ClassExpression(targetFieldType), enumKey));
    }
}
