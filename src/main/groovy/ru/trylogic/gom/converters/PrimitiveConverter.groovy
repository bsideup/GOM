package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression

import static ru.trylogic.gom.config.dsl.MapperProcessor.*;

@CompileStatic
class PrimitiveConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        return ClassHelper.isPrimitiveType(ClassHelper.getUnwrapper(targetFieldType))
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        return nullSafe(sourceFieldValue, new StaticMethodCallExpression(ClassHelper.getWrapper(targetFieldType), VALUE_OF, new ArgumentListExpression(sourceFieldValue)))
    }
}
