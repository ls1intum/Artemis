package de.tum.cit.aet.artemis.domain.enumeration;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * The ProgrammingLanguage enumeration.
 */

public enum ProgrammingLanguage {

    // @formatter:off
    EMPTY(""),
    JAVA("java"),
    PYTHON("py"),
    C("c", "cpp"),
    HASKELL("hs"),
    KOTLIN("kt"),
    VHDL("vhd"),
    ASSEMBLER("asm"),
    SWIFT("swift"),
    OCAML("ml"),
    JAVASCRIPT("js"),
    C_SHARP("cs"),
    C_PLUS_PLUS("c", "h", "cpp", "hpp"),
    SQL("sql"),
    R("R"),
    TYPESCRIPT("ts"),
    RUST("rs"),
    GO("go"),
    MATLAB("m"),
    BASH("sh"),
    RUBY("rb"),
    POWERSHELL("ps1"),
    ADA("adb", "ads"),
    DART("dart"),
    PHP("php");

    private static final Set<ProgrammingLanguage> ENABLED_LANGUAGES = Set.of(
        EMPTY,
        JAVA,
        PYTHON,
        C,
        HASKELL,
        KOTLIN,
        VHDL,
        ASSEMBLER,
        SWIFT,
        OCAML,
        RUST
    );
    // @formatter:on

    private final Set<String> fileExtensions;

    ProgrammingLanguage(String... fileExtension) {
        this.fileExtensions = Set.of(fileExtension);
    }

    public Set<String> getFileExtensions() {
        return fileExtensions;
    }

    public static Set<ProgrammingLanguage> getEnabledLanguages() {
        return ENABLED_LANGUAGES;
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
