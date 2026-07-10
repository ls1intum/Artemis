package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Detects content that cannot be round-tripped through the generation pipeline's UTF-8 text maps. */
public final class BinaryContent {

    private BinaryContent() {
    }

    /**
     * @param bytes complete file content
     * @return whether the bytes contain NUL or invalid UTF-8
     */
    public static boolean isBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return false;
        }
        catch (CharacterCodingException e) {
            return true;
        }
    }

    /**
     * Detects binary content across the complete file so a late marker cannot make a scaffolded binary look like an orphaned text file. Unreadable paths are not silently
     * protected from normal orphan handling.
     *
     * @param path the file to inspect
     * @return whether the complete file contains NUL or invalid UTF-8
     */
    public static boolean isBinaryFile(Path path) {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == 0) {
                        return true;
                    }
                }
            }
            return false;
        }
        catch (MalformedInputException | UnmappableCharacterException e) {
            return true;
        }
        catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
