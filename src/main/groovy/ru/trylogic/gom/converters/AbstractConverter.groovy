package ru.trylogic.gom.converters

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.control.CompilationUnit
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.dsl.MapperProcessor

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*

@CompileStatic
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
    
    String generateMethodName(ClassNode classNode) {
        while(true) {
            def randomName = "converter" + System.currentTimeMillis();

            if(classNode.getMethods(randomName).empty) {
                return randomName;
            }
        }
    }

    boolean isList(ClassNode classNode) {
        return isOrImplements(classNode, ClassHelper.LIST_TYPE);
    }

    boolean isSet(ClassNode classNode) {
        return isOrImplements(classNode, ClassHelper.makeWithoutCaching(Set, false));
    }

    boolean isCollection(ClassNode classNode) {
        return isOrImplements(classNode, ClassHelper.makeWithoutCaching(Collection, false));
    }

    boolean isIterable(ClassNode classNode) {
        return isOrImplements(classNode, ClassHelper.makeWithoutCaching(Iterable, false));
    }

    boolean isMap(ClassNode classNode) {
        return isOrImplements(classNode, ClassHelper.MAP_TYPE);
    }

    TernaryExpression nullSafe(Expression sourceFieldValue, Expression value) {
        return new TernaryExpression(notNullExpr(sourceFieldValue), value, ConstantExpression.NULL);
    }
}
