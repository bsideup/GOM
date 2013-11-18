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
        }
        
        Class a
        Class b
        
        String toA;
        String toB;
        
        Set<Field> fields = new HashSet<>();
    }
    
    Set<Mapping> mappings = new HashSet<>();
}
