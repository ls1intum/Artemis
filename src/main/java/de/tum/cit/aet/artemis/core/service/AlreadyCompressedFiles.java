package de.tum.cit.aet.artemis.core.service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;

/**
 * Decides whether a file's content is already compressed and should therefore be stored rather than deflated again.
 *
 * <p>
 * An Artemis archive nests compressed files by design: a course archive holds one ZIP per repository, and an exercise
 * export holds a ZIP per repository inside a further ZIP. Deflating those a second time measured a 2.5% gain against
 * 50% on the plain CSV files in the same archive, so the outer pass spends CPU on the entire payload for almost
 * nothing. Storing them keeps the archive a normal ZIP that any tool can open, and skips that work.
 */
final class AlreadyCompressedFiles {

    /**
     * Extensions whose content carries its own compression: what Artemis nests itself, the obvious media and archive
     * formats, and the office documents that are ZIP containers in their own right and are the bulk of what a course
     * archive carries as attachments. Anything not listed keeps being deflated, which is the safe default.
     */
    private static final Set<String> EXTENSIONS = Set.of("zip", "jar", "war", "gz", "tgz", "bz2", "xz", "7z", "rar", "png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "webm",
            "pdf", "docx", "xlsx", "pptx", "odt", "ods", "odp");

    private AlreadyCompressedFiles() {
    }

    /**
     * @param path the file to inspect; only its name is read, never its content
     * @return true if the file is already compressed and should be stored in a ZIP rather than deflated
     */
    static boolean matches(Path path) {
        return EXTENSIONS.contains(FilenameUtils.getExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT));
    }
}
