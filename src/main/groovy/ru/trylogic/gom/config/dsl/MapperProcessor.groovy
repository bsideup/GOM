package ru.trylogic.gom.config.dsl

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.CompilationUnit
import ru.trylogic.gom.GOM
import ru.trylogic.gom.Transformer
import ru.trylogic.gom.config.GOMConfig
import ru.trylogic.gom.config.GOMConfig.Direction
import ru.trylogic.gom.config.GOMConfig.Mapping
import ru.trylogic.gom.converters.Converter

import static org.codehaus.groovy.ast.Parameter.EMPTY_ARRAY;

@CompileStatic
class MapperProcessor implements CompilationUnitAware, Opcodes {

    public static final String VALUE_OF = "valueOf"
    public static final String TO_STRING = "toString"
    public static final String GOM_FIELD_NAME = "gom"
    
    CompilationUnit compilationUnit;
    
    GOMConfig config;
    
    List<Converter> converters;

    MapperProcessor(CompilationUnit compilationUnit, GOMConfig config) {
        this.compilationUnit = compilationUnit
        this.config = config
        
        converters = config.converters*.newInstance();

        converters*.init(compilationUnit, config, this);
    }

    Set<InnerClassNode> process(ClassNode classNode) {
        return config.mappings.collect { Mapping it -> processMapping(classNode, it) }.toSet()
    }
    
    InnerClassNode processMapping(ClassNode classNode, Mapping mapping) {
        int counter = 0;

        String className;
        while (true) {
            counter++;
            className = classNode.getName() + '$' + counter

            if (!classNode.innerClasses.any { InnerClassNode it -> it.name.equalsIgnoreCase(className) }) {
                break;
            }
        }

        final InnerClassNode mapperClassNode = new InnerClassNode(classNode, className, ACC_PUBLIC | ACC_STATIC, ClassHelper.OBJECT_TYPE);
        mapperClassNode.anonymous = true;

        mapperClassNode.addProperty(GOM_FIELD_NAME, ACC_PUBLIC, ClassHelper.makeWithoutCaching(GOM), null, null, null);

        ClassNode aClassNode = ClassHelper.makeWithoutCaching(mapping.a);
        ClassNode bClassNode = ClassHelper.makeWithoutCaching(mapping.b);
        
        def transformerInterfaceClassNode = ClassHelper.makeWithoutCaching(Transformer, false);
        transformerInterfaceClassNode.usingGenerics = true;
        transformerInterfaceClassNode.genericsTypes = [new GenericsType(aClassNode), new GenericsType(bClassNode)];
        mapperClassNode.addInterface(transformerInterfaceClassNode);

        mapperClassNode.addMethod(generateTypeGetter("getSourceType", aClassNode));
        mapperClassNode.addMethod(generateTypeGetter("getTargetType", bClassNode));

        mapperClassNode.addMethod(generateToMethod(Direction.A, mapperClassNode, mapping, aClassNode, bClassNode));
        mapperClassNode.addMethod(generateToMethod(Direction.B, mapperClassNode, mapping, bClassNode, aClassNode));
        
        return mapperClassNode;
    }

    MethodNode generateTypeGetter(String name, ClassNode node) {
        ClassNode nodeClass = ClassHelper.makeWithoutCaching(Class, false);
        nodeClass.usingGenerics = true;
        nodeClass.genericsTypes = [new GenericsType(node)];

        return new MethodNode(name, ACC_PUBLIC, nodeClass, EMPTY_ARRAY, null, new ReturnStatement(new ClassExpression(node)));
    }

    MethodNode generateToMethod(Direction direction, InnerClassNode mapperClassNode, Mapping mapping, ClassNode targetClassNode, ClassNode sourceClassNode) {

        def sourceParameter = new Parameter(sourceClassNode, '$source')

        def value = generateFieldValue(direction, mapperClassNode, targetClassNode, new VariableExpression(sourceParameter))
        def toMethodStatement = new ExpressionStatement(value);
        return new MethodNode(direction.toMethodName, ACC_PUBLIC, targetClassNode, [sourceParameter] as Parameter[], null, toMethodStatement)
    }
    
    Expression generateFieldValue(Direction direction, InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        //TODO warn about no mapping
        return converters.find {Converter it -> it.match(targetFieldType, sourceFieldValue)}?.generateFieldValue(mapperClassNode, targetFieldType, sourceFieldValue, direction);
    }
}
