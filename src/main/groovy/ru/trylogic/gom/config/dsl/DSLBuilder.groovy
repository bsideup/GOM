package ru.trylogic.gom.config.dsl

abstract class DSLBuilder<TYPE> {
    TYPE result;

    DSLBuilder(TYPE result, Closure spec) {
        this.result = result
        if(spec) {
            spec.delegate = this;
            spec.call();
        }
    }
    
    TYPE build() {
        return result;
    }
}
