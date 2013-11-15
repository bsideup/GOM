package ru.trylogic.gom.config.dsl

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention (RetentionPolicy.SOURCE)
@Target ([ElementType.TYPE])
@GroovyASTTransformationClass (["ru.trylogic.gom.config.dsl.DSLConfigBuilderTransformation"])
public @interface DSLConfigBuilder {
    boolean enabled() default true;

}