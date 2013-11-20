package ru.trylogic.gom.config.dsl.converters

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;
import static org.codehaus.groovy.ast.Parameter.EMPTY_ARRAY;

import static ru.trylogic.gom.config.dsl.MapperProcessor.*;

class KnownMappingConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, ClassNode sourceFieldType) {
        return config.mappings.any { it.a.name.equalsIgnoreCase(targetFieldType.name) && it.b.name.equalsIgnoreCase(sourceFieldType.name)}
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        //TODO caching

        def targetClassExpression = new ClassExpression(targetFieldType)
        def sourceClassExpression = new ClassExpression(sourceFieldValue.type)

        def mapper = new MethodCallExpression(new PropertyExpression(THIS_EXPRESSION, GOM_FIELD_NAME), "getTransformer", new ArgumentListExpression(targetClassExpression, sourceClassExpression));

        return new MethodCallExpression(mapper, TO_A_METHOD_NAME, new ArgumentListExpression(sourceFieldValue));
    }
}
