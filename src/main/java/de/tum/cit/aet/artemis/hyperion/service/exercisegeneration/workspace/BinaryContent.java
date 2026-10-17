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
     * @param bytes the complete file content, not a prefix, so a marker appearing late is still seen
     * @return whether the content contains NUL or invalid UTF-8
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
     * Scans the complete file rather than a prefix, so a binary marker appearing late cannot make a scaffolded binary look like an orphaned text file.
     *
     * @param path the file to inspect
     * @return whether the file contains NUL or invalid UTF-8; {@code false} for an unreadable path, leaving it to normal orphan handling
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
