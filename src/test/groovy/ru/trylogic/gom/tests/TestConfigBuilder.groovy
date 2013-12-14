package ru.trylogic.gom.tests

import ru.trylogic.gom.config.dsl.DSLConfigBuilder
import ru.trylogic.gom.config.dsl.DSLConfigBuilderBase
import ru.trylogic.gom.tests.data.Person
import ru.trylogic.gom.tests.data.Person.Address
import ru.trylogic.gom.tests.data.PersonDTO
import ru.trylogic.gom.tests.data.PersonDTO.AddressDTO

@DSLConfigBuilder
class TestConfigBuilder extends DSLConfigBuilderBase {

    public static final String STREET_PARTS_GLUE = " ";
    public static final String NAME_GLUE = " ";
    
    {
        mapping { Address a, AddressDTO b ->
            toA { new Address(street: b?.streetParts?.join(STREET_PARTS_GLUE), zipCode: b?.zipCode?.toString()) }
            toB { new AddressDTO(streetParts: a?.street?.split(STREET_PARTS_GLUE), zipCode: a?.zipCode != null ? Integer.valueOf(a?.zipCode) : 0) }
        }
            
        mapping { Person a, PersonDTO b ->
            field (a.phone, b.aPhone)

            field (a.name) {
                toA { b.firstName + NAME_GLUE + b.secondName }
            }

            field(b.firstName) {
                toB { elementOrNull(a.name?.split(NAME_GLUE), 0) }
            }

            field(b.secondName) {
                toB { elementOrNull(a.name?.split(NAME_GLUE), 1) }
            }
        }
    }
    
    static <T> T elementOrNull(T[] elements, int index) {
        if(elements == null) {
            return null;
        }
        
        if(elements.size() <= index) {
            return null;
        }
        
        return elements[index];
    }
}