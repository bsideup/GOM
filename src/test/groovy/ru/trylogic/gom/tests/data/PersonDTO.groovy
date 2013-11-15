package ru.trylogic.gom.tests.data

class PersonDTO {
    
    static class AddressDTO {
        List<String> streetParts
        
        int zipCode
    }
    
    String name;

    String aPhone;
    
    AddressDTO address;
}
