package ru.trylogic.gom.tests.data

class Person {
    
    static class Address {
        String zipCode;
        
        String street;
    }
    
    static enum Sex {
        MALE,
        FEMALE
    }
    
    String name;
    
    String phone;
    
    String age;
    
    Sex sex;
    
    Address address;
}

