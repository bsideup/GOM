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

        def person = new PersonDTO(firstName: firstName, secondName: secondName, sex: sex, age: age, aPhone: phone, address: new AddressDTO(streetParts: streetParts, zipCode: zipCode), friends: friends)
        person.addressNotes = new PersonDTO.AddressNotes();
        person.addressNotes.put(person.address, currentAddressNote);
        person.favouriteAnimals = favouriteAnimals;
        
        Person result = mapper.toA(person)

        expect:
        result.phone == phone
        result.age == age.toString()
        result.sex == Sex.valueOf(Sex, sex.name())
        result.name == firstName + TestConfigBuilder.NAME_GLUE + secondName
        result.address?.zipCode == zipCode.toString()
        result.address?.street == streetParts.join(TestConfigBuilder.STREET_PARTS_GLUE)
        
        result.friends != null
        result.friends.size() == 1
        result.friends.any {it.name == friends[0].firstName + TestConfigBuilder.NAME_GLUE + friends[0].secondName}
        
        result.addressNotes.keySet().first() == result.address
        
        result.addressNotes.get(result.address) == currentAddressNote

        result.favouriteAnimals != null
        result.favouriteAnimals.size() == 2
        result.favouriteAnimals.any {it == favouriteAnimals[0]}
        result.favouriteAnimals.any {it == favouriteAnimals[1]}

        where:
        firstName | secondName | age | sex           | phone | streetParts               | zipCode | friends                            | currentAddressNote | favouriteAnimals
        "John"    | "Smith"    | 18  | SexDTO.MALE   | "911" | ["Katusepapi", "23/25"]   | 100500  | [new PersonDTO(firstName: "Jack")] | "Great flat!"      | ["cat", "panda"]
    }

    def "test to b"(){

        def person = new Person(name: name, sex: sex, age: age, phone: phone, address: new Address(street: street, zipCode: zipCode), friends: friends)
        person.addressNotes = new HashMap<>();
        person.addressNotes.put(person.address, currentAddressNote);
        person.favouriteAnimals = favouriteAnimals;
        
        PersonDTO result = mapper.toB(person)

        expect:
        result.aPhone == phone
        result.age == age.toInteger()
        result.sex == SexDTO.valueOf(SexDTO, sex.name())
        result.firstName == name.split(TestConfigBuilder.NAME_GLUE)[0]
        result.secondName == name.split(TestConfigBuilder.NAME_GLUE)[1]
        result.address?.zipCode == zipCode.toInteger()
        result.address?.streetParts == street.split(TestConfigBuilder.STREET_PARTS_GLUE)
        
        result.friends != null
        result.friends.size() == 1
        result.friends.any {(it.firstName + TestConfigBuilder.NAME_GLUE + it.secondName) == friends[0].name}

        result.addressNotes.keySet().first() == result.address

        result.addressNotes.get(result.address) == currentAddressNote

        result.favouriteAnimals != null
        result.favouriteAnimals.size() == 2
        result.favouriteAnimals.any {it == favouriteAnimals[0]}
        result.favouriteAnimals.any {it == favouriteAnimals[1]}

        where:
        name         | age  | sex      | phone | street             | zipCode     | friends                          | currentAddressNote | favouriteAnimals
        "John Smith" | "18" | Sex.MALE | "911" | "Katusepapi 23/25" | "100500"    | [new Person(name: "Jack Jones")] | "Great flat!"      | ["cat", "panda"]
    }
}
