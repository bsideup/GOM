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
    
    static class FriendsList extends ArrayList<PersonDTO> {
        FriendsList() {
        }

        FriendsList(Collection<? extends PersonDTO> personDTOs) {
            super(personDTOs)
        }
    }
    
    static class AddressNotes extends HashMap<AddressDTO, String> {
        AddressNotes(Map<? extends AddressDTO, ? extends String> map) {
            super(map)
        }

        AddressNotes() {
        }
    }

    String firstName;
    
    String secondName;

    String aPhone;
    
    Integer age;

    SexDTO sex;
    
    AddressDTO address;

    FriendsList friends;

    AddressNotes addressNotes;

    Set<String> favouriteAnimals;
}
