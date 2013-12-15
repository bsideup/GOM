package ru.trylogic.gom.config.dsl

import groovy.transform.CompileStatic

@CompileStatic
abstract class DSLBuilder<TYPE> {
    TYPE result;

    DSLBuilder(TYPE result, Closure spec) {
        this.result = result
        if(spec) {
            spec.delegate = this;
            spec.resolveStrategy = Closure.DELEGATE_FIRST;
            spec.call();
        }
    }
    
    TYPE build() {
        return result;
    }
}
