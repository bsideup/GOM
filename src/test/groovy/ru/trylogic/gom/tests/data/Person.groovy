package ru.trylogic.gom.tests.data

import groovy.transform.EqualsAndHashCode

class Person {

    static enum Sex {
        MALE,
        FEMALE
    }
    
    @EqualsAndHashCode
    static class Address {
        String zipCode;
        
        String street;
    }
    
    String name;
    
    String phone;
    
    String age;
    
    Sex sex;
    
    Address address;
    
    Collection<Person> friends;
    
    Map<Address, String> addressNotes;
    
    HashSet<String> favouriteAnimals;
}

