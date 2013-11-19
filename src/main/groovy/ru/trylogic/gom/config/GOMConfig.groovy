package ru.trylogic.gom.config

import groovy.transform.ToString

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

    GOMConfig() {
        this(new HashSet<Mapping>())
    }

    GOMConfig(Set<Mapping> mappings) {
        this.mappings = mappings
    }
}
