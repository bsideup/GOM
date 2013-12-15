package ru.trylogic.gom.tests

import ru.trylogic.gom.GOM
import ru.trylogic.gom.Transformer
import ru.trylogic.gom.tests.data.Person
import ru.trylogic.gom.tests.data.PersonDTO
import spock.lang.Specification

import static ru.trylogic.gom.tests.data.Person.*
import static ru.trylogic.gom.tests.data.PersonDTO.*

import static ru.trylogic.gom.tests.TestConfigBuilder.*;

class SimpleTest extends Specification {

    Transformer<Person, PersonDTO> mapper;

    def setup() {
        def builder = new TestConfigBuilder();
        
        def gom = new GOM(builder);
        
        mapper = gom.getTransformer(Person, PersonDTO)
    }

    def "test to a"(){

        def person = new PersonDTO(firstName, secondName, phone, age, sex, new AddressDTO(streetParts, zipCode), new FriendsList(friends), new AddressNotes(), favouriteAnimals)
        person.addressNotes.put(person.address, currentAddressNote);
        
        Person result = mapper.toA(person)

        expect:
        result.phone == phone
        result.age == age.toString()
        result.sex == Sex.valueOf(Sex, sex.name())
        result.name == firstName + NAME_GLUE + secondName
        result.address?.zipCode == zipCode.toString()
        result.address?.street == streetParts.join(STREET_PARTS_GLUE)
        
        result.friends != null
        result.friends.size() == 1
        result.friends.any {it.name == friends[0].firstName + NAME_GLUE + friends[0].secondName}
        
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

        def person = new Person(name, phone, age, sex, new Address(zipCode, street), friends, [:], favouriteAnimals)

        person.addressNotes.put(person.address, currentAddressNote);
        
        PersonDTO result = mapper.toB(person)

        expect:
        result.aPhone == phone
        result.age == age.toInteger()
        result.sex == SexDTO.valueOf(SexDTO, sex.name())
        result.firstName == name.split(NAME_GLUE)[0]
        result.secondName == name.split(NAME_GLUE)[1]
        result.address?.zipCode == zipCode.toInteger()
        result.address?.streetParts == street.split(STREET_PARTS_GLUE)
        
        result.friends != null
        result.friends.size() == 1
        result.friends.any {(it.firstName + NAME_GLUE + it.secondName) == friends[0].name}

        result.addressNotes.keySet().first() == result.address

        result.addressNotes.get(result.address) == currentAddressNote

        result.favouriteAnimals != null
        result.favouriteAnimals.size() == 2
        result.favouriteAnimals.any {it == favouriteAnimals[0]}
        result.favouriteAnimals.any {it == favouriteAnimals[1]}

        where:
        name         | age  | sex      | phone | street             | zipCode     | friends                          | currentAddressNote | favouriteAnimals
        "John Smith" | "18" | Sex.MALE | "911" | "Katusepapi 23/25" | "100500"    | [new Person(name: "Jack Jones")] | "Great flat!"      | new HashSet<String>(["cat", "panda"])
    }
}
