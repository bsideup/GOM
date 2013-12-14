package ru.trylogic.gom

interface Transformer<A_TYPE, B_TYPE> {
    
    Class<? extends A_TYPE> getSourceType();
    
    Class<? extends B_TYPE> getTargetType();

    A_TYPE a(B_TYPE b);

    B_TYPE b(A_TYPE a);
    
    
    GOM getGom();
    
    void setGom(GOM value);
}
