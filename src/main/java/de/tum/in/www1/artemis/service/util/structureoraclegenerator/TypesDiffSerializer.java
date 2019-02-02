package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;

import static de.tum.in.www1.artemis.service.util.structureoraclegenerator.SerializerUtil.serializeModifiers;

/**
 * This class is used to serialize a TypesDiff object.
 */
public class TypesDiffSerializer extends StdSerializer<TypesDiff> {
	
    public TypesDiffSerializer() {
        this(null);
    }

    public TypesDiffSerializer(Class<TypesDiff> typesDiffClass) {
        super(typesDiffClass);
    }

    @Override
    public void serialize(TypesDiff typesDiff, JsonGenerator jsonGenerator, SerializerProvider provider) {
        try {
            // Serialize type properties.
            jsonGenerator.writeObjectFieldStart("class");
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("name", typesDiff.name);
            jsonGenerator.writeStringField("package", typesDiff.packageName);

            if(typesDiff.isInterface) {
                jsonGenerator.writeBooleanField("isInterface", true);
            }
            if(typesDiff.isEnum) {
                jsonGenerator.writeBooleanField("isEnum", true);
            }
            if(typesDiff.isAbstract) {
                jsonGenerator.writeBooleanField("isAbstract", true);
            }
            if(!typesDiff.superClassName.isEmpty()){
                jsonGenerator.writeStringField("superclass", typesDiff.superClassName);
            }
            if(typesDiff.superInterfacesNames.size() > 0) {
                jsonGenerator.writeArrayFieldStart("interfaces");
                jsonGenerator.writeStartArray();
                for(CtTypeReference<?> superInterface : typesDiff.superInterfacesNames) {
                    if(!superInterface.isImplicit()) {
                        jsonGenerator.writeString(superInterface.getSimpleName());
                    }
                }
                jsonGenerator.writeEndArray();
            }

            jsonGenerator.writeEndObject();

            // Serialize methods.
            if(!typesDiff.methods.isEmpty()) {
                jsonGenerator.writeObjectFieldStart("methods");

                jsonGenerator.writeStartObject();
                jsonGenerator.writeStartArray();

                for(CtMethod<?> method : typesDiff.methods) {
                    jsonGenerator.writeStartObject();

                    jsonGenerator.writeStringField("name", method.getSimpleName());

                    serializeModifiers(jsonGenerator, method.getModifiers());

                    jsonGenerator.writeArrayFieldStart("parameters");
                    jsonGenerator.writeStartArray();
                    for(CtParameter<?> parameter : method.getParameters()) {
                        if(!parameter.isImplicit()) {
                            jsonGenerator.writeString(parameter.getType().getSimpleName());
                        }
                    }
                    jsonGenerator.writeEndArray();

                    jsonGenerator.writeStringField("returnType", method.getType().getSimpleName());
                }

                jsonGenerator.writeEndArray();
                jsonGenerator.writeEndObject();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
