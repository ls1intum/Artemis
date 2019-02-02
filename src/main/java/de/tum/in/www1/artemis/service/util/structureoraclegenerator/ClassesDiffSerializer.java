package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static de.tum.in.www1.artemis.service.util.structureoraclegenerator.SerializerUtil.serializeModifiers;
import static de.tum.in.www1.artemis.service.util.structureoraclegenerator.SerializerUtil.serializeParameters;

public class ClassesDiffSerializer extends StdSerializer<ClassesDiff> {

    public ClassesDiffSerializer() {
        this(null);
    }

    public ClassesDiffSerializer(Class<ClassesDiff> classesDiffClass) { super(classesDiffClass); }

    @Override
    public void serialize(ClassesDiff classesDiff, JsonGenerator jsonGenerator, SerializerProvider provider) {
        try{
            // Serialize attributes.
            jsonGenerator.writeArrayFieldStart("attributes");

            jsonGenerator.writeStartArray();
            for(CtField<?> attribute : classesDiff.attributes) {
                serializeModifiers(jsonGenerator, attribute.getModifiers());

                jsonGenerator.writeStringField("type", attribute.getType().getSimpleName());

                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();

            // Serialize constructors.
            jsonGenerator.writeArrayFieldStart("constructors");

            jsonGenerator.writeStartArray();
            for(CtConstructor<?> constructor : classesDiff.constructors) {
                jsonGenerator.writeStartObject();

                serializeModifiers(jsonGenerator, constructor.getModifiers());

                jsonGenerator.writeArrayFieldStart("parameters");
                jsonGenerator.writeStartArray();

                for(CtParameter<?> parameter : constructor.getParameters()) {
                    if(parameter.isImplicit()) { continue; }

                    if(constructor.getDeclaringType().isEnum()) {
                        jsonGenerator.writeString("String");
                        jsonGenerator.writeString("int");
                    }

                    jsonGenerator.writeString(parameter.getType().getSimpleName());
                }
                jsonGenerator.writeEndArray();
            }
        }  catch (IOException e) {
            e.printStackTrace();
        }
    }

}
