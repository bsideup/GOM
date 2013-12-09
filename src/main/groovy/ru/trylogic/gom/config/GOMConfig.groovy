package ru.trylogic.gom.config

import groovy.transform.ToString
import ru.trylogic.gom.converters.*

@ToString
class GOMConfig {
    @ToString
    static class Mapping {
        @ToString
        static class Field {
            String aName
            String bName
            
            String a;
            String b;

            Field() {
            }

            Field(String aName, String bName, String a, String b) {
                this.aName = aName
                this.bName = bName
                this.a = a
                this.b = b
            }
        }
        
        Class a
        Class b
        
        String toA;
        String toB;
        
        Set<Field> fields;

        Mapping() {
            this(null, null, null, null, new HashSet<Field>());
        }

        Mapping(Class a, Class b, String toA, String toB, Set<Field> fields) {
            this.a = a
            this.b = b
            this.toA = toA
            this.toB = toB
            this.fields = fields
        }
    }
    
    Set<Mapping> mappings;

    List<Converter> converters = [
            new KnownMappingConverter(),
            new DerivedMatchConverter(),
            new MapConverter(),
            new CollectionConverter(),
            new EnumConverter(),
            new PrimitiveConverter(),
            new StringConverter()
    ]

    GOMConfig() {
        this(new HashSet<Mapping>())
    }

    GOMConfig(Set<Mapping> mappings) {
        this.mappings = mappings
    }
}
