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

        Person a = mapper.toA(new PersonDTO(name: name, sex: sex, age: age, aPhone: phone, address: new AddressDTO(streetParts: streetParts, zipCode: zipCode), friends: friends))

        expect:
        a.phone == phone
        a.age == age.toString()
        a.sex == Sex.valueOf(Sex, sex.name())
        a.name == name
        a.address?.zipCode == zipCode.toString()
        a.address?.street == streetParts.join(TestConfigBuilder.STREET_PARTS_GLUE)
        
        a.friends != null
        a.friends.size() == 2
        a.friends.any {it.name == friends[0].name}
        a.friends.any {it.name == friends[1].name}

        where:
        name    | age | sex           | phone | streetParts               | zipCode | friends
        "John"  | 18  | SexDTO.MALE   | "911" | ["Katusepapi", "23/25"]   | 100500  | [new PersonDTO(name: "Jack"), new PersonDTO(name: "Otto")]
    }

    def "test to b"(){

        PersonDTO b = mapper.toB(new Person(name: name, sex: sex, age: age, phone: phone, address: new Address(street: street, zipCode: zipCode), friends : friends))

        expect:
        b.aPhone == phone
        b.age == age.toInteger()
        b.sex == SexDTO.valueOf(SexDTO, sex.name())
        b.name == name
        b.address?.zipCode == zipCode.toInteger()
        b.address?.streetParts == street.split(TestConfigBuilder.STREET_PARTS_GLUE)
        
        b.friends != null
        b.friends.size() == 2
        b.friends.any {it.name == friends[0].name}
        b.friends.any {it.name == friends[1].name}

        where:
        name   | age  | sex      | phone | street             | zipCode     | friends
        "John" | "18" | Sex.MALE | "911" | "Katusepapi 23/25" | "100500"    | [new Person(name: "Jack"), new Person(name: "Otto")]
    }
}
