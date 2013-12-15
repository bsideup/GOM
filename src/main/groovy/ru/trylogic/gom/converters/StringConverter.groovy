package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.Expression

@CompileStatic
class StringConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        return targetFieldType == ClassHelper.STRING_TYPE;
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        return (Expression) macro {
            $v{sourceFieldValue}?.toString()
        }
    }
}
