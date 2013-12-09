package ru.trylogic.gom.config

import ru.trylogic.gom.Transformer

public interface ConfigBuilder {
    
    Collection<Transformer> getTransformers(); 
}