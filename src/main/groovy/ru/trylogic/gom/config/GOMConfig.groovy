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
        
        Set<Field> fields = new HashSet<>();
    }
    
    boolean mapDynamic
    
    Set<Mapping> mappings = new HashSet<>();
}