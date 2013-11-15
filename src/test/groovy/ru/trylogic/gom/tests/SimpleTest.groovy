package ru.trylogic.gom.tests

import ru.trylogic.gom.GOM
import ru.trylogic.gom.Transformer
import ru.trylogic.gom.tests.data.Person
import ru.trylogic.gom.tests.data.PersonDTO
import ru.trylogic.gom.tests.data.PersonDTO.AddressDTO
import spock.lang.Specification

class SimpleTest extends Specification {

    Transformer<Person, PersonDTO> mapper;

    def setup() {
        def builder = new TestConfigBuilder();
        
        def gom = new GOM(builder);
        
        mapper = gom.getTransformer(Person, PersonDTO)
    }

    def "basic test"(){
        
        Person a = mapper.toA(new PersonDTO(name: name, aPhone: phone, address: new AddressDTO(streetParts: streetParts, zipCode: zipCode)))

        expect:
        a.phone == phone
        a.name == name
        a.address?.zipCode == zipCode.toString()
        a.address?.street == streetParts.join(TestConfigBuilder.STREET_PARTS_GLUE)
        
        where:
        name    | phone | streetParts               | zipCode
        "John"  | "911" | ["Katusepapi", "23/25"]   | 100500
    }
}
