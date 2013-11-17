package ru.trylogic.gom.config.dsl

import ru.trylogic.gom.config.ConfigBuilder
import ru.trylogic.gom.config.GOMConfig

abstract class DSLConfigBuilderBase extends DSLBuilder<GOMConfig> implements ConfigBuilder {

    DSLConfigBuilderBase() {
        super(new GOMConfig(), null);
    }

    static class GOMMappingBuilder extends DSLBuilder<GOMConfig.Mapping> {

        GOMMappingBuilder(Class a, Class b, Closure spec) {
            super(new GOMConfig.Mapping(a: a, b: b), spec)
        }

        static class GOMFieldBuiler extends DSLBuilder<GOMConfig.Mapping.Field> {

            GOMFieldBuiler(String aName, String bName, Closure spec) {
                super(new GOMConfig.Mapping.Field(aName: aName, bName: bName), spec)
            }

            def a(cl) {
                result.a = cl;
            }

            def b(cl) {
                result.b = cl;
            }
        }
        
        def toA(cl) {
            
        }
        
        def toB(cl) {
            
        }

        def field(String aName, String bName) {
            field(aName, bName, null);
        }

        def field(String name, @DelegatesTo(GOMFieldBuiler) Closure spec) {
            field(name, name, spec);
        }

        def field(String aName, String bName, @DelegatesTo(GOMFieldBuiler) Closure spec) {
            result.fields << new GOMFieldBuiler(aName, bName, spec).build();
        }
    }
    
    boolean mapDynamic = false;
    
    abstract def getMappers();
    
    GOMConfig build() {
        return result;
    }

    def mapping(Class a, Class b) {
        mapping(a, b, null);
    }

    def mapping(Class a, Class b, @DelegatesTo(GOMMappingBuilder) Closure spec) {
        result.mappings << new GOMMappingBuilder(a, b, spec).build();
    }
    
}
