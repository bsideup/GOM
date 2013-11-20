package ru.trylogic.gom.config.dsl.converters

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.dsl.MapperProcessor

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

    boolean isCastingTo(ClassNode type, ClassNode to) {
        return type.isDerivedFrom(to) || type.implementsInterface(to);
    }
}
