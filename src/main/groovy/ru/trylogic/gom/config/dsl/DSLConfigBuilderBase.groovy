package ru.trylogic.gom.config.dsl

import ru.trylogic.gom.Transformer
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

        static class GOMFieldBuiler extends DSLBuilder<GOMConfig.Field> {

            GOMFieldBuiler(String aName, String bName, Closure spec) {
                super(new GOMConfig.Field(aName: aName, bName: bName), spec)
            }

            def toA(cl) {
                result.a = cl;
            }

            def toB(cl) {
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

        def field(String name, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = GOMFieldBuiler) Closure spec) {
            field(name, name, spec);
        }

        def field(String aName, String bName, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = GOMFieldBuiler) Closure spec) {
            result.fields << new GOMFieldBuiler(aName, bName, spec).build();
            check();
        }
    }

    
    Collection<Transformer> getTransformers() {}
    
    GOMConfig build() {
        return result;
    }

    @Deprecated
    protected def doMapping(Class a, Class b, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = GOMMappingBuilder) Closure spec) {
        def mapping = new GOMMappingBuilder(a, b, spec).build()
        result.mappings << mapping;

        result.mappings << new GOMConfig.Mapping(mapping.b, mapping.a, mapping.toB, mapping.toA, mapping.fields.collect {
            new GOMConfig.Field(it.bName, it.aName, it.b, it.a)
        }.toSet())
    }

    def mapping(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = GOMMappingBuilder) Closure spec) {
    }
    
}
