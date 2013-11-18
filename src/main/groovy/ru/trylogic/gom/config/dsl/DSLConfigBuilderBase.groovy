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
        
        private void check() {
            if((result.fields != null && !result.fields.empty) && (result.toA != null || result.toB != null)) {
                throw new Exception("You can't use both of fields and toA or toB");
            }
        }
        
        def toA(cl) {
            result.toA = cl;
            check();
        }
        
        def toB(cl) {
            result.toB = cl;
            check();
        }

        def field(String aName, String bName) {
            field(aName, bName, null);
        }

        def field(String name, @DelegatesTo(GOMFieldBuiler) Closure spec) {
            field(name, name, spec);
        }

        def field(String aName, String bName, @DelegatesTo(GOMFieldBuiler) Closure spec) {
            result.fields << new GOMFieldBuiler(aName, bName, spec).build();
            check();
        }
    }
    
    abstract def getMappers();
    
    GOMConfig build() {
        return result;
    }

    def mapping(Class a, Class b) {
        mapping(a, b, null);
    }

    def mapping(Class a, Class b, @DelegatesTo(GOMMappingBuilder) Closure spec) {
        def mapping = new GOMMappingBuilder(a, b, spec).build()
        result.mappings << mapping;
        
        result.mappings << new GOMConfig.Mapping(a : mapping.b, b : mapping.a, toA : mapping.toB, toB : mapping.toA, fields: mapping.fields.collect {
            new GOMConfig.Mapping.Field(aName: it.bName, bName: it.aName, a : it.b, b : it.a)
        })
    }
    
}
