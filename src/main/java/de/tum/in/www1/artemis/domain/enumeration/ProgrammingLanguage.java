package de.tum.in.www1.artemis.domain.enumeration;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * The ProgrammingLanguage enumeration.
 */

public enum ProgrammingLanguage {

    JAVA("java"), PYTHON("py"), C("c", "cpp"), HASKELL("hs"), KOTLIN("kt"), VHDL("vhd"), ASSEMBLER("asm"), SWIFT("swift"), OCAML("ml"), EMPTY(""), RUST("rs");

    private final Set<String> fileExtensions;

    ProgrammingLanguage(String... fileExtension) {
        this.fileExtensions = Set.of(fileExtension);
    }

    public Set<String> getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Check if the given path matches one of the file extensions of the programming language.
     * For the EMPTY language, this method always returns true if the path is not empty.
     *
     * @param path the path to check
     * @return true if the path is not empty and matches one of the file extensions, false otherwise
     */
    public boolean matchesFileExtension(String path) {
        return StringUtils.isNotBlank(path) && (this == EMPTY || fileExtensions.stream().anyMatch(extension -> path.endsWith("." + extension)));
    }
}
