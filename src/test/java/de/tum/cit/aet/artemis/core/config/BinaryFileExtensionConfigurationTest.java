package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BinaryFileExtensionConfigurationTest {

    static Stream<String> binaryFileExtensions() {
        return Stream.of(
                // Images
                "image.png", "photo.jpg", "photo.jpeg", "photo.heic", "animation.gif", "scan.tiff", "design.psd",
                // Documents
                "report.pdf", "essay.doc", "essay.docx", "data.xls", "data.xlsx", "slides.ppt", "slides.pptx", "doc.pages", "sheet.numbers", "pres.key", "text.odt",
                // Compressed files
                "archive.zip", "archive.rar", "archive.7z", "archive.tar", "archive.gz", "archive.bz2", "archive.xz", "archive.lzma", "archive.lz4", "archive.zst",
                // Executables
                "program.exe", "installer.msi", "firmware.bin", "tool.app", "script.sh", "script.bat",
                // Compiled Java files
                "lib.jar", "Main.class", "app.war", "app.ear",
                // Compiled Python files
                "module.pyc", "module.pyo", "module.pyd", "data.npy",
                // Compiled C/C++/.NET/Go files
                "main.o", "lib.so", "lib.a", "lib.dylib", "lib.lib", "lib.dll", "lib.exp", "debug.pdb",
                // Compiled Rust files
                "lib.rlib",
                // Compiled Swift files
                "module.swiftmodule", "module.swiftdoc", "lib.swiftdylib",
                // Disk images
                "os.iso", "installer.dmg", "disk.vmdk",
                // Bytecode and intermediate representations
                "code.bc", "code.ll", "module.wasm", "module.wast",
                // Machine learning model binaries
                "model.pb", "model.onnx", "model.tflite", "model.pt", "model.h5",
                // Android and iOS binary formats
                "app.apk", "app.ipa",
                // Firmware and embedded binaries
                "firmware.hex", "firmware.elf", "firmware.uf2",
                // Audio files
                "song.mp3", "audio.wav", "music.flac", "track.aac", "sound.ogg", "clip.wma", "song.m4a", "audio.aiff", "voice.opus", "melody.mid", "melody.midi",
                // Video files
                "video.mp4", "clip.avi", "recording.mov", "movie.mkv", "clip.wmv", "stream.flv", "video.webm", "clip.m4v", "video.mpeg", "video.mpg",
                // Miscellaneous development-related binaries
                "disk.img", "floppy.dsk", "vm.qcow2", "database.mdb", "data.sqlite");
    }

    @ParameterizedTest
    @MethodSource("binaryFileExtensions")
    void shouldDetectBinaryFiles(String filePath) {
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile(filePath)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Main.java", "index.html", "style.css", "app.ts", "readme.md", "config.yml", "data.json", "script.py", "Makefile", "notes.txt" })
    void shouldNotDetectTextFilesAsBinary(String filePath) {
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile(filePath)).isFalse();
    }

    @Test
    void shouldHandleCaseInsensitiveExtensions() {
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile("image.PNG")).isTrue();
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile("song.MP3")).isTrue();
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile("video.AVI")).isTrue();
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile("archive.ZIP")).isTrue();
    }

    @Test
    void shouldHandlePathsWithDirectories() {
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile("src/main/resources/image.png")).isTrue();
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile("assets/audio/song.mp3")).isTrue();
        assertThat(BinaryFileExtensionConfiguration.isBinaryFile("src/main/java/Main.java")).isFalse();
    }
}
