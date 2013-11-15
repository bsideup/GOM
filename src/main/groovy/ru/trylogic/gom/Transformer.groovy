package ru.trylogic.gom

interface Transformer<SOURCE, TARGET> {
    
    Class<? extends SOURCE> getSourceType();
    
    Class<? extends TARGET> getTargetType();

    SOURCE toA(TARGET b);
    
    //TARGET toB(SOURCE a);
}
