package ru.trylogic.gom.converters

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.control.CompilationUnit
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.dsl.MapperProcessor

interface Converter {
    
    void init(CompilationUnit compilationUnit, GOMConfig config, MapperProcessor mapperProcessor);
    
    boolean match(ClassNode targetFieldType, Expression sourceFieldValue);

    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue);
}
