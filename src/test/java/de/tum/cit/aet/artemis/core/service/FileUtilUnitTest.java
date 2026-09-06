package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDParentTreeValue;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

class FileUtilUnitTest {

    public static final Path exportTestRootPath = Path.of(".", "local", "server-integration-test", "exportTest");

    private static final Path lineEndingsUnixPath = exportTestRootPath.resolve("LineEndingsUnix.java");

    private static final Path lineEndingsWindowsPath = exportTestRootPath.resolve("LineEndingsWindows.java");

    @AfterEach
    @BeforeEach
    void deleteFiles() throws IOException {
        RepositoryExportTestUtil.safeDeleteDirectory(exportTestRootPath);
    }

    /**
     * The containment check in {@link FileUtil#resolveWithinDirectoryElseThrow(Path, String)} is lexical, so a symlink
     * already sitting at the destination would point outside the directory. Exclusive creation is what refuses it:
     * the write must fail and the link target must be left untouched.
     */
    @Test
    void refusesToWriteThroughASymlinkPlantedAtTheDestination(@TempDir Path tempDir) throws Exception {
        Path insideDirectory = Files.createDirectories(tempDir.resolve("temp"));
        Path outsideTarget = tempDir.resolve("escaped.txt");
        FileUtils.writeStringToFile(outsideTarget.toFile(), "original", StandardCharsets.UTF_8);
        Path plantedLink = insideDirectory.resolve("Temp_1_lecture.pdf");
        try {
            Files.createSymbolicLink(plantedLink, outsideTarget);
        }
        catch (UnsupportedOperationException | IOException e) {
            // Creating symlinks needs privileges this platform may withhold; nothing to assert if we cannot plant one.
            return;
        }

        try (InputStream inputStream = new ByteArrayInputStream("attacker".getBytes(StandardCharsets.UTF_8))) {
            assertThatThrownBy(() -> FileUtil.writeNewFileElseThrow(inputStream, plantedLink)).isInstanceOf(FileAlreadyExistsException.class);
        }

        assertThat(Files.readString(outsideTarget)).isEqualTo("original");
    }

    @Test
    void writesANewFileAndCreatesMissingParentDirectories(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("nested").resolve("created").resolve("file.txt");

        try (InputStream inputStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))) {
            FileUtil.writeNewFileElseThrow(inputStream, target);
        }

        assertThat(Files.readString(target)).isEqualTo("content");
    }

    /**
     * CREATE_NEW creates the file before the first byte is copied, and the caller only registers the path for deletion
     * once the write returns, so a failure part-way through must not leave a truncated file nobody will clean up.
     */
    @Test
    void removesThePartialFileWhenTheTransferFails(@TempDir Path tempDir) {
        Path target = tempDir.resolve("partial.txt");
        InputStream failingHalfWay = new InputStream() {

            private int remaining = 8;

            @Override
            public int read() throws IOException {
                if (remaining-- > 0) {
                    return 'x';
                }
                throw new IOException("stream broke after writing bytes");
            }
        };

        assertThatThrownBy(() -> FileUtil.writeNewFileElseThrow(failingHalfWay, target)).isInstanceOf(IOException.class).hasMessageContaining("stream broke");

        assertThat(Files.exists(target)).as("the truncated file must not be left behind").isFalse();
    }

    /**
     * The counterpart to the cleanup above: when the open itself fails the path belongs to something else - the planted
     * symlink case - and deleting it would destroy exactly what the exclusive create is there to protect.
     */
    @Test
    void keepsAnExistingFileWhenTheOpenIsRefused(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("existing.txt");
        FileUtils.writeStringToFile(target.toFile(), "original", StandardCharsets.UTF_8);

        try (InputStream inputStream = new ByteArrayInputStream("replacement".getBytes(StandardCharsets.UTF_8))) {
            assertThatThrownBy(() -> FileUtil.writeNewFileElseThrow(inputStream, target)).isInstanceOf(FileAlreadyExistsException.class);
        }

        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    void refusesToOverwriteAnExistingFile(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("file.txt");
        FileUtils.writeStringToFile(target.toFile(), "original", StandardCharsets.UTF_8);

        try (InputStream inputStream = new ByteArrayInputStream("replacement".getBytes(StandardCharsets.UTF_8))) {
            assertThatThrownBy(() -> FileUtil.writeNewFileElseThrow(inputStream, target)).isInstanceOf(FileAlreadyExistsException.class);
        }

        assertThat(Files.readString(target)).isEqualTo("original");
    }

    @Test
    void resolveWithinDirectoryShouldReturnContainedPath() {
        Path baseDirectory = Path.of("/tmp/artemis/files/temp");
        assertThat(FileUtil.resolveWithinDirectoryElseThrow(baseDirectory, "Temp_42_lecture.pdf")).isEqualTo(baseDirectory.resolve("Temp_42_lecture.pdf"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "../../../etc/passwd", "..", "../sibling.pdf", "sub/../../escape.pdf", "/etc/passwd" })
    void resolveWithinDirectoryShouldRejectEscapingFilenames(String filename) {
        Path baseDirectory = Path.of("/tmp/artemis/files/temp");
        assertThatThrownBy(() -> FileUtil.resolveWithinDirectoryElseThrow(baseDirectory, filename)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filename");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void resolveWithinDirectoryShouldRejectBlankFilenames(String filename) {
        assertThatThrownBy(() -> FileUtil.resolveWithinDirectoryElseThrow(Path.of("/tmp/artemis/files/temp"), filename)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void resolveWithinDirectoryShouldRejectSiblingDirectorySharingNamePrefix() {
        // "temp-evil" shares a character prefix with "temp" but is a different directory, so a character-wise
        // comparison would wrongly accept it. Path.startsWith() compares elements, which is why this throws.
        assertThatThrownBy(() -> FileUtil.resolveWithinDirectoryElseThrow(Path.of("/tmp/artemis/files/temp"), "../temp-evil/file.pdf")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    void resolveWithinDirectoryShouldContainEverySanitizedFilename() {
        // The guard has to accept whatever sanitizeFilename() produces, otherwise it would reject legitimate uploads.
        Path baseDirectory = Path.of("/tmp/artemis/files/temp");
        for (String hostile : List.of("../../etc/passwd", "..\\..\\windows\\system32", "/absolute/path.pdf", "..", "....//....//x.pdf")) {
            String sanitized = "Temp_1_" + FileUtil.sanitizeFilename(hostile);
            assertThatNoException().isThrownBy(() -> FileUtil.resolveWithinDirectoryElseThrow(baseDirectory, sanitized));
            assertThat(FileUtil.resolveWithinDirectoryElseThrow(baseDirectory, sanitized).getParent()).isEqualTo(baseDirectory);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "folder/file.txt", "folder/subfolder/file.pdf", "file.docx", "safe_name-123.txt" })
    void testSanitizeFilePath_ValidPaths(String filePath) {
        assertThatNoException().isThrownBy(() -> FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(filePath));
    }

    @ParameterizedTest
    @ValueSource(strings = { "folder/../file.txt", "folder/evil/../../file.txt" })
    void testSanitizeFilePath_InvalidPaths(String filePath) {
        assertThatThrownBy(() -> FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(filePath)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path is not valid!");
    }

    @Test
    void testSanitizeFilePath_EmptyPath() {
        assertThatNoException().isThrownBy(() -> FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(""));
    }

    @Test
    void normalizeEncodingUTF8() throws IOException {
        copyFile("EncodingUTF8.java", "EncodingUTF8.java");
        Charset charset = FileUtil.detectCharset(Files.readAllBytes(exportTestRootPath.resolve("EncodingUTF8.java")));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void normalizeEncodingISO_8559_1() throws IOException {
        copyFile("EncodingISO_8559_1.java", "EncodingISO_8559_1.java");
        final var exportTestPath = exportTestRootPath.resolve("EncodingISO_8559_1.java");
        Charset charset = FileUtil.detectCharset(Files.readAllBytes(exportTestPath));
        assertThat(charset).isEqualTo(StandardCharsets.ISO_8859_1);

        FileUtil.convertToUTF8(exportTestPath);
        charset = FileUtil.detectCharset(Files.readAllBytes(exportTestPath));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void testReplaceVariablesInFileRecursive_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> FileUtil.replaceVariablesInFileRecursive(Path.of("some-path"), new HashMap<>()))
                .withMessageEndingWith("should be replaced but the directory does not exist.");
    }

    @Test
    void testNormalizeLineEndingsDirectory_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> FileUtil.normalizeLineEndingsDirectory(Path.of("some-path")))
                .withMessageEndingWith("should be normalized but the directory does not exist.");
    }

    @Test
    void testConvertToUTF8Directory_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> FileUtil.convertFilesInDirectoryToUtf8(Path.of("some-path")))
                .withMessageEndingWith("should be converted to UTF-8 but the directory does not exist.");
    }

    @Test
    void replacePlaceHolder() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = exportTestRootPath.resolve("pom.xml").toFile();
        String fileContent = Files.readString(pomXml.toPath(), Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        FileUtil.replaceVariablesInFileRecursive(pomXml.getParentFile().toPath(), replacements);
        fileContent = Files.readString(pomXml.toPath(), Charset.defaultCharset());

        assertThat(fileContent).doesNotContain("${exerciseName}").contains("SomeCoolExerciseName");
    }

    @Test
    void replacePlaceHolderIgnoreNames() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = exportTestRootPath.resolve("pom.xml").toFile();
        String fileContent = Files.readString(pomXml.toPath(), Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        FileUtil.replaceVariablesInFileRecursive(pomXml.getParentFile().toPath(), replacements, List.of("pom.xml"));
        fileContent = Files.readString(pomXml.toPath(), Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");
    }

    private static void copyFile(String filePath, String destinationPath) {
        try {
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/repository-export/" + filePath), exportTestRootPath.resolve(destinationPath).toFile());
        }
        catch (IOException ex) {
            fail("Failed while copying test files", ex);
        }
    }

    @Test
    void testHandleSaveFile_noOriginalFilename() {
        MultipartFile file = mock(MultipartFile.class);
        doAnswer(invocation -> null).when(file).getOriginalFilename();
        assertThatThrownBy(() -> FileUtil.handleSaveFile(file, false, false)).isInstanceOf(IllegalArgumentException.class);
        verify(file, times(1)).getOriginalFilename();
    }

    @Test
    void testCopyExistingFileToTarget() throws IOException {
        String payload = "test";
        Path filePath = exportTestRootPath.resolve("testFile.txt");
        FileUtils.writeStringToFile(filePath.toFile(), payload, StandardCharsets.UTF_8);
        Path newFolder = exportTestRootPath.resolve("newFolder");

        Path newPath = FileUtil.copyExistingFileToTarget(filePath, newFolder, FilePathType.COURSE_ICON);
        assertThat(newPath).isNotNull();
        assertThat(Files.readString(newPath, StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void testCopyExistingFileToTarget_newFile() {
        assertThat(FileUtil.copyExistingFileToTarget(null, Path.of(".", "exportTest"), FilePathType.DRAG_ITEM)).isNull();
    }

    @Test
    void testCopyExistingFileToTarget_temporaryFile() {
        // We don't need to create a file here as we expect the method to terminate early
        Path tempPath = Path.of(".", "uploads", "files", "temp", "testFile.txt");
        Path newPath = Path.of(".", "exportTest");
        assertThat(FileUtil.copyExistingFileToTarget(tempPath, newPath, FilePathType.TEMPORARY)).isNull();
    }

    @Test
    void normalizeFileEndingsUnix_noChange() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = Files.readAllBytes(lineEndingsUnixPath).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void normalizeFileEndingsUnix_normalized() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = Files.readAllBytes(lineEndingsUnixPath).length;
        assertThat(size).isEqualTo(129);

        FileUtil.normalizeLineEndings(lineEndingsUnixPath);
        size = Files.readAllBytes(lineEndingsUnixPath).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void normalizeFileEndingsWindows_noChange() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = Files.readAllBytes(lineEndingsWindowsPath).length;
        assertThat(size).isEqualTo(136);
    }

    @Test
    void normalizeFileEndingsWindows_normalized() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = Files.readAllBytes(lineEndingsWindowsPath).length;
        assertThat(size).isEqualTo(136);

        FileUtil.normalizeLineEndings(lineEndingsWindowsPath);
        size = Files.readAllBytes(lineEndingsWindowsPath).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void testMergePdf_nullInput_shouldReturnEmptyOptional() {
        Optional<byte[]> result = FileUtil.mergePdfFiles(null, "");
        assertThat(result).isEmpty();
    }

    @Test
    void testMergePdf_emptyList_shouldReturnEmptyOptional() {
        Optional<byte[]> result = FileUtil.mergePdfFiles(new ArrayList<>(), "list_of_pdfs");
        assertThat(result).isEmpty();
    }

    @Test
    void testMergePdf_nonexistentFiles_shouldReturnPresentEmptyFile() {
        Optional<byte[]> result = FileUtil.mergePdfFiles(List.of(exportTestRootPath.resolve("missing.pdf")), "missing");
        assertThat(result).contains(new byte[0]);
    }

    @Test
    void testMergePdf() throws IOException {
        Path firstPdf = createPdf("testfile1.pdf", List.of(new PDRectangle(100, 100), new PDRectangle(110, 110), new PDRectangle(120, 120)));
        Path secondPdf = createPdf("testfile2.pdf", List.of(new PDRectangle(200, 200), new PDRectangle(210, 210)));

        Optional<byte[]> mergedFile = FileUtil.mergePdfFiles(List.of(firstPdf, secondPdf), "list_of_pdfs");

        assertThat(mergedFile).isPresent();
        try (PDDocument mergedDocument = Loader.loadPDF(mergedFile.orElseThrow())) {
            assertThat(mergedDocument.getNumberOfPages()).isEqualTo(5);
            assertThat(mergedDocument.getDocumentInformation().getTitle()).isEqualTo("list_of_pdfs");
            assertThat(mergedDocument.getPages()).extracting(page -> page.getMediaBox().getWidth()).containsExactly(100F, 110F, 120F, 200F, 210F);
        }
    }

    @Test
    void testMergePdfMalformedStructureTreeUsesPageOnlyFallback() throws IOException {
        Path malformedPdf = createPdf("malformed.pdf", List.of(new PDRectangle(100, 100)));
        addMalformedStructureTreeAndUriLink(malformedPdf);
        Path secondPdf = createPdf("second.pdf", List.of(new PDRectangle(200, 200)));

        assertThatThrownBy(() -> mergeWithLegacyMode(List.of(malformedPdf, secondPdf))).isInstanceOf(IOException.class).hasMessageContaining("number tree");

        Optional<byte[]> mergedFile = FileUtil.mergePdfFiles(List.of(malformedPdf, secondPdf), "fallback_title");

        assertThat(mergedFile).isPresent();
        try (PDDocument mergedDocument = Loader.loadPDF(mergedFile.orElseThrow())) {
            assertThat(mergedDocument.getNumberOfPages()).isEqualTo(2);
            assertThat(mergedDocument.getDocumentInformation().getTitle()).isEqualTo("fallback_title");
            assertThat(mergedDocument.getPages()).extracting(page -> page.getMediaBox().getWidth()).containsExactly(100F, 200F);
            assertThat(mergedDocument.getDocumentCatalog().getStructureTreeRoot()).isNull();
            assertThat(mergedDocument.getPage(0).getCOSObject().containsKey(COSName.STRUCT_PARENTS)).isFalse();

            PDAnnotationLink link = (PDAnnotationLink) mergedDocument.getPage(0).getAnnotations().getFirst();
            assertThat(link.getCOSObject().containsKey(COSName.STRUCT_PARENT)).isFalse();
            assertThat(link.getAction()).isInstanceOf(PDActionURI.class);
            assertThat(((PDActionURI) link.getAction()).getURI()).isEqualTo("https://example.com/lecture-slide");
            PDAppearanceDictionary appearance = link.getAppearance();
            assertAppearanceHasNoStructureParentReferences(appearance.getNormalAppearance());
            assertAppearanceHasNoStructureParentReferences(appearance.getRolloverAppearance());
            assertAppearanceHasNoStructureParentReferences(appearance.getDownAppearance());

            PDFormXObject outerForm = (PDFormXObject) mergedDocument.getPage(0).getResources().getXObject(COSName.getPDFName("OuterForm"));
            PDFormXObject innerForm = (PDFormXObject) outerForm.getResources().getXObject(COSName.getPDFName("InnerForm"));
            PDImageXObject taggedImage = (PDImageXObject) innerForm.getResources().getXObject(COSName.getPDFName("TaggedImage"));
            assertThat(outerForm.getCOSObject().containsKey(COSName.STRUCT_PARENTS)).isFalse();
            assertThat(innerForm.getCOSObject().containsKey(COSName.STRUCT_PARENTS)).isFalse();
            assertThat(taggedImage.getCOSObject().containsKey(COSName.STRUCT_PARENT)).isFalse();
            assertThat(taggedImage.getWidth()).isEqualTo(2);
            assertThat(taggedImage.getHeight()).isEqualTo(2);
            assertThat(new PDFStreamParser(innerForm).parse()).filteredOn(Operator.class::isInstance).extracting(token -> ((Operator) token).getName()).containsSubsequence("Do",
                    "re", "S");
        }
    }

    @Test
    void testMergePdfWellFormedStructureTreeUsesLegacyMerge() throws IOException {
        Path taggedPdf = createPdf("tagged.pdf", List.of(new PDRectangle(100, 100)));
        addWellFormedStructureTree(taggedPdf);

        Optional<byte[]> mergedFile = FileUtil.mergePdfFiles(List.of(taggedPdf), "legacy_title");

        assertThat(mergedFile).isPresent();
        try (PDDocument mergedDocument = Loader.loadPDF(mergedFile.orElseThrow())) {
            assertThat(mergedDocument.getDocumentInformation().getTitle()).isEqualTo("legacy_title");
            PDStructureTreeRoot mergedStructureTree = mergedDocument.getDocumentCatalog().getStructureTreeRoot();
            assertThat(mergedStructureTree).isNotNull();
            assertThat(mergedStructureTree.getParentTree().getNumbers()).containsKey(0);
            assertThat(mergedDocument.getDocumentCatalog().getMarkInfo().isMarked()).isTrue();
            assertThat(mergedDocument.getPage(0).getCOSObject().getInt(COSName.STRUCT_PARENTS)).isZero();
            PDStructureElement mergedStructureElement = (PDStructureElement) mergedStructureTree.getKids().getFirst();
            assertThat(mergedStructureElement.getPage()).isEqualTo(mergedDocument.getPage(0));
            PDMarkedContentReference markedContentReference = (PDMarkedContentReference) mergedStructureElement.getKids().getFirst();
            assertThat(markedContentReference.getMCID()).isZero();
            assertThat(markedContentReference.getPage()).isEqualTo(mergedDocument.getPage(0));
            assertThat(new PDFStreamParser(mergedDocument.getPage(0)).parse()).filteredOn(Operator.class::isInstance).extracting(token -> ((Operator) token).getName())
                    .containsSubsequence("BDC", "re", "f", "EMC");
        }
    }

    @Test
    void testMergePdfCorruptSourceReturnsEmptyOptional() throws IOException {
        Path corruptPdf = exportTestRootPath.resolve("corrupt.pdf");
        writeFile("corrupt.pdf", "%PDF-1.7\n1 0 obj\n<< /Type /Catalog".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> mergePdf(corruptPdf, PDFMergerUtility.DocumentMergeMode.PDFBOX_LEGACY_MODE)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> mergePdf(corruptPdf, PDFMergerUtility.DocumentMergeMode.OPTIMIZE_RESOURCES_MODE)).isInstanceOf(IOException.class);
        assertThat(FileUtil.mergePdfFiles(List.of(corruptPdf), "corrupt")).isEmpty();
    }

    private static Path createPdf(String filename, List<PDRectangle> pageSizes) throws IOException {
        Path path = exportTestRootPath.resolve(filename);
        Files.createDirectories(path.getParent());
        try (PDDocument document = new PDDocument()) {
            pageSizes.stream().map(PDPage::new).forEach(document::addPage);
            document.save(path.toFile());
        }
        return path;
    }

    private static void addMalformedStructureTreeAndUriLink(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDStructureTreeRoot structureTreeRoot = new PDStructureTreeRoot();
            COSDictionary parentTree = new COSDictionary();
            COSArray numbers = new COSArray();
            numbers.add(COSInteger.ZERO);
            numbers.add(COSInteger.ONE);
            parentTree.setItem(COSName.NUMS, numbers);
            structureTreeRoot.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
            document.getDocumentCatalog().setStructureTreeRoot(structureTreeRoot);

            document.getPage(0).getCOSObject().setInt(COSName.STRUCT_PARENTS, 0);
            PDAnnotationLink link = new PDAnnotationLink();
            link.getCOSObject().setInt(COSName.STRUCT_PARENT, 1);
            link.setRectangle(new PDRectangle(10, 10, 20, 20));
            PDActionURI action = new PDActionURI();
            action.setURI("https://example.com/lecture-slide");
            link.setAction(action);
            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.setNormalAppearance(createAppearanceStream(document, 5));
            COSDictionary rolloverAppearances = new COSDictionary();
            rolloverAppearances.setItem(COSName.getPDFName("Default"), createAppearanceStream(document, 6));
            appearance.setRolloverAppearance(new PDAppearanceEntry(rolloverAppearances));
            appearance.setDownAppearance(createAppearanceStream(document, 7));
            link.setAppearance(appearance);
            document.getPage(0).getAnnotations().add(link);

            PDFormXObject innerForm = new PDFormXObject(document);
            innerForm.setBBox(new PDRectangle(20, 20));
            PDResources innerFormResources = new PDResources();
            PDImageXObject taggedImage = LosslessFactory.createFromImage(document, new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));
            taggedImage.setStructParent(4);
            innerFormResources.put(COSName.getPDFName("TaggedImage"), taggedImage);
            innerForm.setResources(innerFormResources);
            innerForm.setStructParents(2);
            try (PDFormContentStream contentStream = new PDFormContentStream(innerForm)) {
                contentStream.drawImage(taggedImage, 1, 1, 2, 2);
                contentStream.addRect(1, 1, 10, 10);
                contentStream.stroke();
            }

            PDFormXObject outerForm = new PDFormXObject(document);
            outerForm.setBBox(new PDRectangle(20, 20));
            PDResources outerFormResources = new PDResources();
            outerFormResources.put(COSName.getPDFName("InnerForm"), innerForm);
            outerForm.setResources(outerFormResources);
            outerForm.setStructParents(3);
            try (PDFormContentStream contentStream = new PDFormContentStream(outerForm)) {
                contentStream.drawForm(innerForm);
            }

            PDResources pageResources = new PDResources();
            pageResources.put(COSName.getPDFName("OuterForm"), outerForm);
            COSDictionary xObjects = pageResources.getCOSObject().getCOSDictionary(COSName.XOBJECT);
            xObjects.setItem(COSName.getPDFName("NullXObject"), COSNull.NULL);
            COSDictionary unreadableXObject = new COSDictionary();
            unreadableXObject.setItem(COSName.TYPE, COSName.XOBJECT);
            xObjects.setItem(COSName.getPDFName("UnreadableXObject"), unreadableXObject);
            document.getPage(0).setResources(pageResources);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(0))) {
                contentStream.drawForm(outerForm);
            }
            document.save(path.toFile());
        }
    }

    private static PDAppearanceStream createAppearanceStream(PDDocument document, int structureParent) throws IOException {
        PDAppearanceStream appearanceStream = new PDAppearanceStream(document);
        appearanceStream.setBBox(new PDRectangle(20, 20));
        appearanceStream.setResources(new PDResources());
        appearanceStream.setStructParents(structureParent);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, appearanceStream)) {
            contentStream.addRect(2, 2, 5, 5);
            contentStream.stroke();
        }
        return appearanceStream;
    }

    private static void assertAppearanceHasNoStructureParentReferences(PDAppearanceEntry appearanceEntry) throws IOException {
        List<PDAppearanceStream> appearanceStreams = appearanceEntry.isStream() ? List.of(appearanceEntry.getAppearanceStream())
                : List.copyOf(appearanceEntry.getSubDictionary().values());
        assertThat(appearanceStreams).isNotEmpty();
        for (PDAppearanceStream appearanceStream : appearanceStreams) {
            assertThat(appearanceStream.getCOSObject().containsKey(COSName.STRUCT_PARENTS)).isFalse();
            assertThat(new PDFStreamParser(appearanceStream).parse()).filteredOn(Operator.class::isInstance).extracting(token -> ((Operator) token).getName())
                    .containsSubsequence("re", "S");
        }
    }

    private static void addWellFormedStructureTree(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDStructureTreeRoot structureTreeRoot = new PDStructureTreeRoot();
            PDStructureElement structureElement = new PDStructureElement("Document", structureTreeRoot);
            structureElement.setPage(document.getPage(0));
            PDMarkedContentReference markedContentReference = new PDMarkedContentReference();
            markedContentReference.setPage(document.getPage(0));
            markedContentReference.setMCID(0);
            structureElement.appendKid(markedContentReference);
            structureTreeRoot.appendKid(structureElement);

            COSArray parentTreeEntry = new COSArray();
            parentTreeEntry.add(structureElement);
            PDNumberTreeNode parentTree = new PDNumberTreeNode(PDParentTreeValue.class);
            parentTree.setNumbers(Map.of(0, new PDParentTreeValue(parentTreeEntry)));
            structureTreeRoot.setParentTree(parentTree);
            structureTreeRoot.setParentTreeNextKey(1);
            document.getPage(0).getCOSObject().setInt(COSName.STRUCT_PARENTS, 0);
            document.getDocumentCatalog().setStructureTreeRoot(structureTreeRoot);
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            document.getDocumentCatalog().setMarkInfo(markInfo);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(0))) {
                contentStream.beginMarkedContent(COSName.P, 0);
                contentStream.addRect(10, 10, 20, 20);
                contentStream.fill();
                contentStream.endMarkedContent();
            }
            document.save(path.toFile());
        }
    }

    private static void mergeWithLegacyMode(List<Path> paths) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        for (Path path : paths) {
            merger.addSource(path.toFile());
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            merger.setDestinationStream(outputStream);
            merger.mergeDocuments(null);
        }
    }

    private static void mergePdf(Path path, PDFMergerUtility.DocumentMergeMode mergeMode) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDocumentMergeMode(mergeMode);
        merger.addSource(path.toFile());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            merger.setDestinationStream(outputStream);
            merger.mergeDocuments(null);
        }
    }

    @Test
    void testDeleteFiles_shouldNotThrowException() {
        Path path = Path.of("some-random-path-which-does-not-exist");
        assertThatNoException().isThrownBy(() -> FileUtil.deleteFiles(List.of(path)));
    }

    public static void writeFile(String destinationPath, String content) {
        try {
            FileUtils.writeByteArrayToFile(exportTestRootPath.resolve(destinationPath).toFile(), content.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ex) {
            fail("Failed while writing test files", ex);
        }
    }

    public static void writeFile(String destinationPath, byte[] bytes) {
        try {
            FileUtils.writeByteArrayToFile(exportTestRootPath.resolve(destinationPath).toFile(), bytes);
        }
        catch (IOException ex) {
            fail("Failed while writing test files", ex);
        }
    }

    /*
     * We have to save the content as a String as git will automatically convert the line endings based on the developer's OS, therefore we do not store it as a file in
     * src/test/resources/test-data
     */
    public static final String FILE_WITH_UNIX_LINE_ENDINGS = """
            public class LineEndings {

                public void someMethod() {
                    // Some logic inside here
                    someService.call();
                }
            }
            """;

    public static final String FILE_WITH_WINDOWS_LINE_ENDINGS = """
            public class LineEndings {\r
            \r
                public void someMethod() {\r
                    // Some logic inside here\r
                    someService.call();\r
                }\r
            }\r
            """;
}
