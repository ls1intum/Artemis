package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import de.tum.in.www1.artemis.service.util.structureoraclegenerator.InterfacesDiff;
import de.tum.in.www1.artemis.service.util.structureoraclegenerator.TypesDiffSerializer;

public class InterfacesDiffSerializer extends StdSerializer<InterfacesDiff> {

    private TypesDiffSerializer typesDiffSerializer;

    public InterfacesDiffSerializer() {
        this(null);
    }

    public InterfacesDiffSerializer(Class<InterfacesDiff> interfacesDiffClass) {
        super(interfacesDiffClass);
    }
	
	@Override
    public  void serialize(InterfacesDiff interfacesDiff, JsonGenerator jgen, SerializerProvider provider) {
        typesDiffSerializer.serialize(interfacesDiff, jgen, provider);
    }

}
