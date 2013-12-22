package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import ru.trylogic.gom.config.GOMConfig

@CompileStatic
class PrimitiveConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        return ClassHelper.isPrimitiveType(ClassHelper.getUnwrapper(targetFieldType))
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue, GOMConfig.Direction direction) {
        return nullSafe(sourceFieldValue, (Expression) macro {
            $v{new ClassExpression(ClassHelper.getWrapper(targetFieldType))}.valueOf($v{sourceFieldValue})
        })
    }
}
