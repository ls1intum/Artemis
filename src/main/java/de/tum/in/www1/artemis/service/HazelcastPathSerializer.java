package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.HAZELCAST_PATH_SERIALIZER_ID;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.hazelcast.nio.serialization.ByteArraySerializer;

public class HazelcastPathSerializer implements ByteArraySerializer<Path> {

    @Override
    public byte[] write(Path path) throws IOException {
        return path.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Path read(byte[] buffer) throws IOException {
        String pathString = new String(buffer, StandardCharsets.UTF_8);
        return Path.of(pathString);
    }

    @Override
    public int getTypeId() {
        return HAZELCAST_PATH_SERIALIZER_ID;
    }
}
