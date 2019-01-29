package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;

import java.io.IOException;

public class ClassesDiffSerializer extends StdSerializer<ClassesDiff> {

    public ClassesDiffSerializer() {
        this(null);
    }

    public ClassesDiffSerializer(Class<ClassesDiff> classesDiffClass) { super(classesDiffClass); }

    @Override
    public void serialize(ClassesDiff classesDiff, JsonGenerator jgen, SerializerProvider provider) {
        try{
            // Serialize attributes.
            jgen.writeArrayFieldStart("attributes");

            jgen.writeStartArray();
            for(CtField<?> attribute : classesDiff.attributes) {
                jgen.writeStartObject();

                jgen.writeStringField("name", attribute.getSimpleName());

                jgen.writeArrayFieldStart("modifiers");
                jgen.writeStartArray();
                for(ModifierKind modifier : attribute.getModifiers()) {
                    jgen.writeString(modifier.toString());
                }
                jgen.writeEndArray();

                jgen.writeStringField("type", attribute.getType().getSimpleName());

                jgen.writeEndObject();
            }
            jgen.writeEndArray();

            // Serialize constructors.
            jgen.writeArrayFieldStart("constructors");

            jgen.writeStartArray();
            for(CtConstructor<?> constructor : classesDiff.constructors) {
                jgen.writeStartObject();

                jgen.writeArrayFieldStart("modifiers");
                jgen.writeStartArray();
                for(ModifierKind modifier : constructor.getModifiers()) {
                    jgen.writeString(modifier.toString());
                }
                jgen.writeEndArray();

                jgen.writeArrayFieldStart("parameters");
                jgen.writeStartArray();
                for(CtParameter<?> parameter : constructor.getParameters()) {
                    if(parameter.isImplicit()) { continue; }

                    if(constructor.getDeclaringType().isEnum()) {
                        jgen.writeString("String");
                        jgen.writeString("int");
                    }

                    jgen.writeString(parameter.getType().getSimpleName());
                }
                jgen.writeEndArray();
            }
        }  catch (IOException e) {
            e.printStackTrace();
        }
    }

}
