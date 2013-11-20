package ru.trylogic.gom.tests.data

import groovy.transform.EqualsAndHashCode

class PersonDTO {
    
    static enum SexDTO {
        FEMALE,
        MALE
    }
    
    @EqualsAndHashCode
    static class AddressDTO {
        List<String> streetParts

        int zipCode
    }
    
    String name;

    String aPhone;
    
    Integer age;

    SexDTO sex;
    
    AddressDTO address;
    
    List<PersonDTO> friends;

    Map<AddressDTO, String> addressNotes;

    Set<String> favouriteAnimals;
}
