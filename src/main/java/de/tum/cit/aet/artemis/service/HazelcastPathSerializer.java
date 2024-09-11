package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST_PATH_SERIALIZER_ID;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.springframework.context.annotation.Profile;

import com.hazelcast.nio.serialization.ByteArraySerializer;

@Profile(PROFILE_CORE)
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
