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
    
    def friendsCompareCLosure = { PersonDTO toElement, Person sourceElement ->
        sourceElement.name == toElement.firstName + NAME_GLUE + toElement.secondName
    }

    def setup() {
        def builder = new TestConfigBuilder();
        
        def gom = new GOM(builder);
        
        mapper = gom.getTransformer(Person, PersonDTO)
    }
    
    def "test double conversion"() {

        def person = new PersonDTO(firstName, secondName, phone, age, sex, new AddressDTO(streetParts, zipCode), new FriendsList(friends), new AddressNotes(), favouriteAnimals)
        
        def result = mapper.toB(mapper.toA(person))

        expect:
        person.hashCode() == result.hashCode()

        where:
        firstName | secondName | age | sex           | phone | streetParts               | zipCode | friends                            | currentAddressNote | favouriteAnimals
        "John"    | "Smith"    | 18  | SexDTO.MALE   | "911" | ["Katusepapi", "23/25"]   | 100500  | [new PersonDTO(firstName: "Jack")] | "Great flat!"      | ["cat", "panda"]
    }

    def "test to a"(){

        def person = new PersonDTO(firstName, secondName, aPhone, age, sex, new AddressDTO(streetParts, zipCode), new FriendsList(friends), new AddressNotes(), favouriteAnimals)
        person.addressNotes.put(person.address, currentAddressNote);
        
        Person result = mapper.toA(person)

        expect:
        result.phone == aPhone
        result.age == age.toString()
        result.sex == Sex.valueOf(Sex, sex.name())
        result.name == firstName + NAME_GLUE + secondName
        result.address?.zipCode == zipCode.toString()
        result.address?.street == streetParts.join(STREET_PARTS_GLUE)

        compare(result.friends, friends, friendsCompareCLosure)
        
        result.addressNotes.keySet().first() == result.address
        result.addressNotes.get(result.address) == currentAddressNote

        compare(result.favouriteAnimals, favouriteAnimals)

        where:
        firstName | secondName | age | sex           | aPhone | streetParts               | zipCode | friends                            | currentAddressNote | favouriteAnimals
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
        
        compare(result.friends, friends, { Person toElement, PersonDTO sourceElement -> friendsCompareCLosure(sourceElement, toElement) })

        result.addressNotes.keySet().first() == result.address
        result.addressNotes.get(result.address) == currentAddressNote

        compare(result.favouriteAnimals, favouriteAnimals)

        where:
        name         | age  | sex      | phone | street             | zipCode     | friends                          | currentAddressNote | favouriteAnimals
        "John Smith" | "18" | Sex.MALE | "911" | "Katusepapi 23/25" | "100500"    | [new Person(name: "Jack Jones")] | "Great flat!"      | new HashSet<String>(["cat", "panda"])
    }


    private <FIRST_TYPE, SECOND_TYPE> boolean compare(Collection<FIRST_TYPE> source, Collection<SECOND_TYPE> to, Closure converter = null) {
        if(to == null) {
            return source == null;
        }

        if(to.size() != source.size()) {
            return false;
        }

        if(converter == null) {
            converter = {SECOND_TYPE toElement, FIRST_TYPE sourceElement -> toElement == sourceElement}
        }

        return to.every {SECOND_TYPE toElement ->
            source.any { FIRST_TYPE sourceElement ->
                converter(toElement, sourceElement)
            }
        }
    }
}
