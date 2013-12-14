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
            toA { AddressDTO address -> new Address(street: address?.streetParts?.join(STREET_PARTS_GLUE), zipCode: address?.zipCode?.toString()) }
            toB { Address address -> new AddressDTO(streetParts: address?.street?.split(STREET_PARTS_GLUE), zipCode: address?.zipCode != null ? Integer.valueOf(address?.zipCode) : 0) }
        }
            
        mapping { Person a, PersonDTO b ->
            field (a.phone, b.aPhone)

            field (a.name) {
                toA { PersonDTO person -> person.firstName + NAME_GLUE + person.secondName }
            }

            field(b.firstName) {
                toB { Person person -> elementOrNull(person.name?.split(NAME_GLUE), 0) }
            }

            field(b.secondName) {
                toB { Person person -> elementOrNull(person.name?.split(NAME_GLUE), 1) }
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