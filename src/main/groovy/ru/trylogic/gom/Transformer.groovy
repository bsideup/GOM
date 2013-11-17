package ru.trylogic.gom

interface Transformer<A_TYPE, B_TYPE> {
    
    Class<? extends A_TYPE> getSourceType();
    
    Class<? extends B_TYPE> getTargetType();

    A_TYPE toA(B_TYPE b);
    
    //TARGET toB(SOURCE a);
    
    
    GOM getGom();
    
    void setGom(GOM value);
}
