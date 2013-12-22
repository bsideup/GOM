package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import ru.trylogic.gom.config.GOMConfig

@CompileStatic
class KnownMappingConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        
        return config.mappings.any { GOMConfig.Mapping it ->
            it.a.name.equalsIgnoreCase(targetFieldType.name) && it.b.name.equalsIgnoreCase(sourceFieldValue.type.name)
        }
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue, GOMConfig.Direction direction) {
        //TODO caching

        return (Expression) macro {
            gom.getTransformer($v{new ClassExpression(targetFieldType)}, $v{new ClassExpression(sourceFieldValue.type)}).toA($v{sourceFieldValue})
        }
    }
}
