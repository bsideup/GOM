package ru.trylogic.gom

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention (RetentionPolicy.SOURCE)
@Target ([ElementType.TYPE])
@GroovyASTTransformationClass (["ru.trylogic.gom.MapperTransformation"])
@interface Mapper {
    Class to();
    
    Class from();
}
