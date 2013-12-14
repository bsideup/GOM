package ru.trylogic.gom.config

import groovy.transform.CompileStatic
import groovy.transform.ToString
import ru.trylogic.gom.converters.*

@ToString
@CompileStatic
class GOMConfig {

    @ToString
    static class Mapping {

        
        Class a
        Class b

        String toA;
        String toB;

        Set<Field> fields;
        boolean inverted;

        Mapping() {
            this(null, null, null, null, new HashSet<Field>(), false);
        }

        Mapping(Class a, Class b, String toA, String toB, Set<Field> fields, boolean inverted) {
            this.a = a
            this.b = b
            this.toA = toA
            this.toB = toB
            this.fields = fields
            this.inverted = inverted;
        }
    }

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

    static enum Direction {
        A("a", "b", "toA"),
        B("b", "a", "toB")

        String parameterName;
        String opositeParameterName;
        String toMethodName;

        Direction(String parameterName, String opositeParameterName, String toMethodName) {
            this.parameterName = parameterName;
            this.opositeParameterName = opositeParameterName;
            this.toMethodName = toMethodName
        }

        String getFieldConverterName(Field field) {
            return (ab(field.aName, field.bName) as String)  + "From" + this.name();
        }

        String getFieldConverterCode(Field field) {
            return ab(field?.a, field?.b);
        }

        String toMethodCode(Mapping mapping) {
            return ab(mapping.toA, mapping.toB);
        }

        String getTargetFieldName(Field field) {
            return ab(field?.bName, field?.aName);
        }

        String getSourceFieldName(Field field) {
            return ab(field?.aName, field?.bName);
        }

        def <T> T ab(T a, T b) {
            switch(this) {
                case A:
                    return a;
                case B:
                    return b;
                default:
                    throw new Exception("unreachable");
            }
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
    ] as List<Converter>

    GOMConfig() {
        this(new HashSet<Mapping>())
    }

    GOMConfig(Set<Mapping> mappings) {
        this.mappings = mappings
    }
}
