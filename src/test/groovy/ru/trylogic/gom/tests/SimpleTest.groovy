package ru.trylogic.gom.tests

import ru.trylogic.gom.GOM
import ru.trylogic.gom.Transformer
import ru.trylogic.gom.tests.data.Person
import ru.trylogic.gom.tests.data.Person.Sex
import ru.trylogic.gom.tests.data.Person.Address
import ru.trylogic.gom.tests.data.PersonDTO
import ru.trylogic.gom.tests.data.PersonDTO.SexDTO
import ru.trylogic.gom.tests.data.PersonDTO.AddressDTO
import spock.lang.Specification

class SimpleTest extends Specification {

    Transformer<Person, PersonDTO> mapper;

    def setup() {
        def builder = new TestConfigBuilder();
        
        def gom = new GOM(builder);
        
        mapper = gom.getTransformer(Person, PersonDTO)
    }

    def "test to a"(){

        Person a = mapper.toA(new PersonDTO(name: name, sex: sex, age: age, aPhone: phone, address: new AddressDTO(streetParts: streetParts, zipCode: zipCode)))

        expect:
        a.phone == phone
        a.age == age.toString()
        a.sex == Sex.valueOf(Sex, sex.name())
        a.name == name
        a.address?.zipCode == zipCode.toString()
        a.address?.street == streetParts.join(TestConfigBuilder.STREET_PARTS_GLUE)

        where:
        name    | age | sex           | phone | streetParts               | zipCode
        "John"  | 18  | SexDTO.MALE   | "911" | ["Katusepapi", "23/25"]   | 100500
    }

    def "test to b"(){

        PersonDTO b = mapper.toB(new Person(name: name, sex: sex, age: age, phone: phone, address: new Address(street: street, zipCode: zipCode)))

        expect:
        b.aPhone == phone
        b.age == age.toInteger()
        b.sex == SexDTO.valueOf(SexDTO, sex.name())
        b.name == name
        b.address?.zipCode == zipCode.toInteger()
        b.address?.streetParts == street.split(TestConfigBuilder.STREET_PARTS_GLUE)

        where:
        name   | age  | sex      | phone | street             | zipCode
        "John" | "18" | Sex.MALE | "911" | "Katusepapi 23/25" | "100500"
    }
}
