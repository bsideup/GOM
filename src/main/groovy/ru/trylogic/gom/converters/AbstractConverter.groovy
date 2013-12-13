package ru.trylogic.gom.converters

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.transform.AbstractASTTransformUtil
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.dsl.MapperProcessor

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.notNullExpr

abstract class AbstractConverter implements Converter {
    
    CompilationUnit compilationUnit;
    GOMConfig config;
    
    MapperProcessor mapperProcessor;
    
    @Override
    void init(CompilationUnit compilationUnit, GOMConfig config, MapperProcessor mapperProcessor) {
        this.compilationUnit = compilationUnit;
        this.config = config;
        this.mapperProcessor = mapperProcessor;
    }

    TernaryExpression nullSafe(Expression sourceFieldValue, Expression value) {
        return new TernaryExpression(notNullExpr(sourceFieldValue), value, ConstantExpression.NULL);
    }
}
