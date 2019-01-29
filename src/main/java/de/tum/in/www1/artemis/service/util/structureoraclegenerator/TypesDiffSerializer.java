package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;

import java.io.IOException;

public class TypesDiffSerializer extends StdSerializer<TypesDiff> {
	
    public TypesDiffSerializer() {
        this(null);
    }

    public TypesDiffSerializer(Class<TypesDiff> typesDiffClass) {
        super(typesDiffClass);
    }

    @Override
    public void serialize(TypesDiff typesDiff, JsonGenerator jgen, SerializerProvider provider) {
        try {
            // Serialize type properties.
            jgen.writeObjectFieldStart("class");
            jgen.writeStartObject();

            jgen.writeStringField("name", typesDiff.name);
            jgen.writeStringField("package", typesDiff.packageName);
            if(typesDiff.isInterface) {
                jgen.writeBooleanField("isInterface", true);
            }
            if(typesDiff.isEnum) {
                jgen.writeBooleanField("isEnum", true);
            }
            if(typesDiff.isAbstract) {
                jgen.writeBooleanField("isAbstract", true);
            }
            if(!typesDiff.superClassName.isEmpty()){
                jgen.writeStringField("superclass", typesDiff.superClassName);
            }
            if(typesDiff.superInterfacesNames.length > 0) {
                jgen.writeArrayFieldStart("interfaces");
                jgen.writeStartArray(typesDiff.superInterfacesNames.length);
                jgen.writeEndArray();
            }

            jgen.writeEndObject();

            // Serialize methods.
            if(!typesDiff.methods.isEmpty()) {
                jgen.writeObjectFieldStart("methods");

                jgen.writeStartObject();
                jgen.writeStartArray();

                for(CtMethod<?> method : typesDiff.methods) {
                    jgen.writeStartObject();

                    jgen.writeStringField("name", method.getSimpleName());

                    jgen.writeArrayFieldStart("modifiers");
                    jgen.writeStartArray();
                    for(ModifierKind modifier : method.getModifiers()) {
                        jgen.writeString(modifier.toString());
                    }
                    jgen.writeEndArray();

                    jgen.writeArrayFieldStart("parameters");
                    jgen.writeStartArray();
                    for(CtParameter<?> parameter : method.getParameters()) {
                        if(!parameter.isImplicit()) {
                            jgen.writeString(parameter.getType().getSimpleName());
                        }
                    }
                    jgen.writeEndArray();

                    jgen.writeStringField("returnType", method.getType().getSimpleName());
                }

                jgen.writeEndArray();
                jgen.writeEndObject();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
