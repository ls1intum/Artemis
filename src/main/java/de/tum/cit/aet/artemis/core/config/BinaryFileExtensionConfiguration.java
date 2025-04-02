package de.tum.cit.aet.artemis.core.config;

import java.util.Set;

/**
 * Provides a list of binary file extensions which can be used to filter returned files in the business logic of
 * the application.
 */
public class BinaryFileExtensionConfiguration {

    /**
     * A list of common binary file extensions.
     * Extensions must be lower-case with a leading dot.
     */
    private static final Set<String> binaryFileExtensions = Set.of(
            // Images
            ".png", ".jpg", ".jpeg", ".heic", ".gif", ".tiff", ".psd",

            // Documents
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".pages", ".numbers", ".key", ".odt",

            // Compressed files
            ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz", ".lzma", ".lz4", ".zst",

            // Executables
            ".exe", ".msi", ".bin", ".app", ".sh", ".bat",

            // Compiled Java files
            ".jar", ".class", ".war", ".ear",

            // Compiled Python files
            ".pyc", ".pyo", ".pyd",

            // Compiled C/C++/.NET/Go files
            ".o", ".so", ".a", ".dylib", ".lib", ".dll", ".exp", ".pdb",

            // Compiled Rust files
            ".rlib",

            // Compiled Swift files
            ".swiftmodule", ".swiftdoc", ".swiftdylib",

            // Disk images (often used in software development environments)
            ".iso", ".dmg", ".vmdk",

            // Bytecode and intermediate representations
            ".bc", ".ll", ".wasm", ".wast",

            // Machine learning model binaries
            ".pb", ".onnx", ".tflite", ".pt", ".h5",

            // Android and iOS binary formats
            ".apk", ".ipa",

            // Firmware and embedded binaries
            ".hex", ".elf", ".uf2",

            // Miscellaneous development-related binaries
            ".img", ".dsk", ".qcow2", ".mdb", ".sqlite");

    /**
     * Checks if a file is a binary file based on its file extension.
     *
     * @param filePath the path of the file to check
     * @return true if the file is a binary file, false otherwise
     */
    public static boolean isBinaryFile(String filePath) {
        String lowerCasePath = filePath.toLowerCase();
        return binaryFileExtensions.stream().anyMatch(lowerCasePath::endsWith);
    }
}
