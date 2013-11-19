package ru.trylogic.gom.tests.data

class PersonDTO {
    
    static enum SexDTO {
        FEMALE,
        MALE
    }
    
    static class AddressDTO {
        List<String> streetParts

        Integer zipCode
    }
    
    String name;

    String aPhone;
    
    Integer age;

    SexDTO sex;
    
    AddressDTO address;
    
    List<PersonDTO> friends;
}
