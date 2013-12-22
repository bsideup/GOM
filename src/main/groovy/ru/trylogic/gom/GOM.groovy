package ru.trylogic.gom

import ru.trylogic.gom.config.ConfigBuilder

class GOM {
    
    Map<Class, Map<Class, Transformer>> transformers = new HashMap<>();

    GOM(ConfigBuilder configBuilder) {
        configBuilder.getTransformers().each { Transformer it ->
            if(!transformers.containsKey(it.sourceType)) {
                transformers.put(it.sourceType, new HashMap<Class, Transformer>());
            }
            
            transformers.get(it.sourceType).put(it.targetType, it);
        }
    }

    Transformer getTransformer(Class<?> classA, Class<?> classB) {

        def transformersByA = transformers.get(classA)
        if(transformersByA == null) {
            return null;
        }

        return transformersByA.get(classB);
    }
}
