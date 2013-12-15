package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.Expression

@CompileStatic
class DerivedMatchConverter extends AbstractConverter {
    @Override
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue) {
        if(!targetFieldType.isDerivedFrom(sourceFieldValue.type)) {
            return false;
        }

        def targetFieldGenericTypes = targetFieldType.genericsTypes
        def sourceFieldGenericTypes = sourceFieldValue.type.genericsTypes
        
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
