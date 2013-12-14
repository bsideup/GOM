package ru.trylogic.gom.config.dsl

import groovy.transform.CompilationUnitAware
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
import ru.trylogic.gom.config.GOMConfig.Field

import static org.codehaus.groovy.transform.AbstractASTTransformUtil.*;

import static org.codehaus.groovy.ast.expr.ArgumentListExpression.EMPTY_ARGUMENTS;
import static org.codehaus.groovy.ast.expr.VariableExpression.THIS_EXPRESSION;
import static org.codehaus.groovy.ast.Parameter.EMPTY_ARRAY;

class MapperProcessor implements CompilationUnitAware, Opcodes {

    public static final String VALUE_OF = "valueOf"
    public static final String TO_STRING = "toString"
    public static final String GOM_FIELD_NAME = "gom"
    
    CompilationUnit compilationUnit;
    
    GOMConfig config;

    MapperProcessor(CompilationUnit compilationUnit, GOMConfig config) {
        this.compilationUnit = compilationUnit
        this.config = config

        config.converters*.init(compilationUnit, config, this);
    }

    Set<InnerClassNode> process(ClassNode classNode) {
        return config.mappings.collect { processMapping(classNode, it) }
    }
    
    InnerClassNode processMapping(ClassNode classNode, Mapping mapping) {
        int counter = 0;

        String className;
        while (true) {
            counter++;
            className = classNode.getName() + '$' + counter

            if (!classNode.innerClasses.any { it.name.equalsIgnoreCase(className) }) {
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
        def toMethodCode = direction.toMethodCode(mapping)
        if(toMethodCode != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(toMethodCode);

            return new MethodNode(direction.toMethodName, ACC_PUBLIC, targetClassNode, [new Parameter(sourceClassNode, mapping.inverted ? direction.parameterName : direction.opositeParameterName)] as Parameter[], null, closure.code);
        }

        def methodBody = new BlockStatement();

        def sourceParameter = new Parameter(sourceClassNode, '$source')
        
        methodBody.statements << new IfStatement(equalsNullExpr(new VariableExpression(sourceParameter)), new BlockStatement([new ReturnStatement(new ConstantExpression(null))], new VariableScope()), new EmptyStatement());
        
        def targetVariable = new VariableExpression('$result', targetClassNode)
        methodBody.statements << declStatement(targetVariable, new ConstructorCallExpression(targetClassNode, EMPTY_ARGUMENTS));

        targetClassNode.fields.each { targetField ->
            if((targetField.modifiers & ACC_SYNTHETIC) ) {
                return;
            }

            Expression value = generateFieldAssign(direction, mapping, mapperClassNode, targetClassNode, sourceClassNode, targetField, sourceParameter);

            if(value == null) {
                return;
            }

            def propertyExpression = new PropertyExpression(targetVariable, targetField.name)
            methodBody.statements << assignStatement(propertyExpression, value);
        }

        methodBody.statements << new ReturnStatement(targetVariable)

        return new MethodNode(direction.toMethodName, ACC_PUBLIC, targetClassNode, [sourceParameter] as Parameter[], null, methodBody)
    }
    
    Expression generateFieldAssign(Direction direction, Mapping mapping, InnerClassNode mapperClassNode, ClassNode targetClassNode, ClassNode sourceClassNode, FieldNode targetField, Parameter sourceParameter) {
        Field fieldConfig = mapping.fields?.find { direction.getSourceFieldName(it) == targetField.name }
        String sourceFieldConverterCode = direction.getFieldConverterCode(fieldConfig);
        if(sourceFieldConverterCode != null) {
            def closure = new ClosureCompiler(compilationUnit).compile(sourceFieldConverterCode);

            def methodName = direction.getFieldConverterName(fieldConfig)
            mapperClassNode.addMethod(methodName, ACC_PUBLIC, targetField.type, [new Parameter(sourceClassNode, mapping.inverted ? direction.parameterName : direction.opositeParameterName)] as Parameter[], null, closure.code);

            return new MethodCallExpression(THIS_EXPRESSION, methodName, new ArgumentListExpression(sourceParameter));
        }

        FieldNode sourceField = sourceClassNode.getField(direction.getTargetFieldName(fieldConfig) ?: targetField.name);
        
        if(sourceField == null) {
            return null;
        }
        
        def sourceFieldValue = new PropertyExpression(new VariableExpression(sourceParameter), sourceField.name)
        sourceFieldValue.type = sourceClassNode.getField(sourceField.name).type
        
        return generateFieldValue(mapperClassNode, targetField.type, sourceFieldValue)
    }
    
    Expression generateFieldValue(InnerClassNode mapperClassNode, ClassNode targetFieldType, Expression sourceFieldValue) {
        //TODO warn about no mapping
        return config.converters.find {it.match(targetFieldType, sourceFieldValue.type)}?.generateFieldValue(mapperClassNode, targetFieldType, sourceFieldValue);
    }
}
