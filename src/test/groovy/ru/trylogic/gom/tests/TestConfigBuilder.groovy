package ru.trylogic.gom.tests

import ru.trylogic.gom.config.dsl.DSLConfigBuilder
import ru.trylogic.gom.config.dsl.DSLConfigBuilderBase
import ru.trylogic.gom.tests.data.Person
import ru.trylogic.gom.tests.data.Person.Address
import ru.trylogic.gom.tests.data.PersonDTO
import ru.trylogic.gom.tests.data.PersonDTO.AddressDTO

@DSLConfigBuilder
class TestConfigBuilder extends DSLConfigBuilderBase {

    public static final String STREET_PARTS_GLUE = " "
    
    def mappers = [
        mapping(Address, AddressDTO) {
            toA { AddressDTO address ->
                new Address(street: address?.streetParts?.join(STREET_PARTS_GLUE), zipCode: address?.zipCode?.toString())
            }
            
            toB { Address address ->
                new AddressDTO(streetParts: address?.street?.split(STREET_PARTS_GLUE), zipCode: Integer.valueOf(address?.zipCode))
            }
        },
            
        mapping(Person, PersonDTO) {
            field ("phone", "aPhone")
        }
    ]
}