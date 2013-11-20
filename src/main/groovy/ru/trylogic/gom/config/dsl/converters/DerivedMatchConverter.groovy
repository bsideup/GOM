package ru.trylogic.gom.config.dsl.converters

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.Expression

class DerivedMatchConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, ClassNode sourceFieldType) {
        if(!targetFieldType.isDerivedFrom(sourceFieldType)) {
            return false;
        }

        def targetFieldGenericTypes = targetFieldType.genericsTypes
        def sourceFieldGenericTypes = sourceFieldType.genericsTypes
        
        boolean genericTypesMatch = true;

        for(int i = 0; i < targetFieldGenericTypes?.size(); i++) {
            genericTypesMatch &= targetFieldGenericTypes[i].type == sourceFieldGenericTypes[i].type;
        }
        
        return genericTypesMatch;
    }

    @Override
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        return sourceFieldValue
    }
}
