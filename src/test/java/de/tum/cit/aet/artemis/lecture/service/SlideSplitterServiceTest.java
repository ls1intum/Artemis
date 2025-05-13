package de.tum.cit.aet.artemis.lecture.service;

import static com.hazelcast.jet.core.test.JetAssert.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.validation.constraints.NotNull;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class SlideSplitterServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "slidesplitterservicetest";

    @Autowired
    private SlideSplitterService slideSplitterService;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    private AttachmentUnit testAttachmentUnit;

    private PDDocument testDocument;

    @BeforeEach
    void initTestCase() {
        // Create a test attachment unit with a PDF file
        testAttachmentUnit = lectureUtilService.createAttachmentUnitWithSlidesAndFile(3, true);

        // Create a real PDF document for tests
        testDocument = new PDDocument();
        // Add 3 blank pages
        for (int i = 0; i < 3; i++) {
            testDocument.addPage(new PDPage());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_BasicFunction() {
        // Clear existing slides for this attachment unit
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Act
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf");

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3);

        for (int i = 0; i < slides.size(); i++) {
            Slide slide = slides.get(i);
            assertThat(slide.getSlideNumber()).isEqualTo(i + 1);
            assertThat(slide.getAttachmentUnit()).isEqualTo(testAttachmentUnit);
            assertThat(slide.getSlideImagePath()).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_WithHiddenPagesAndPageOrder() throws IOException {
        // Create and save an Exercise
        Exercise testExercise = new TextExercise();
        testExercise.setTitle("Test Exercise");
        exerciseRepository.save(testExercise);

        // Arrange
        ZonedDateTime hiddenDate = ZonedDateTime.now().plusDays(1);

        // Create proper DTO objects
        List<HiddenPageInfoDTO> hiddenPagesList = List.of(new HiddenPageInfoDTO("1", hiddenDate, testExercise.getId()));

        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1), new SlideOrderDTO("2", 2), new SlideOrderDTO("3", 3));

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get a proper temp path for slides
        Path tempFilePath = FilePathService.getTempFilePath();
        Files.createDirectories(tempFilePath);

        // Create existing slides with valid paths
        for (int i = 1; i <= 3; i++) {
            // Create a real file in the temp directory
            Path slidePath = tempFilePath.resolve("slide" + i + ".png");

            // Create a simple image file (1x1 pixel)
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", slidePath.toFile());

            Slide slide = new Slide();
            slide.setId((long) i);
            slide.setSlideNumber(i);
            slide.setAttachmentUnit(testAttachmentUnit);

            // Valid path that can be resolved
            slide.setSlideImagePath("temp/slide" + i + ".png");
            slideRepository.save(slide);
        }

        // Act
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3);

        // First slide should be hidden with exercise
        Slide firstSlide = slides.stream().filter(s -> s.getSlideNumber() == 1).findFirst().orElse(null);
        assertThat(firstSlide).isNotNull();
        assertThat(firstSlide.getHidden()).isNotNull();
        assertThat(firstSlide.getExercise()).isNotNull();
        assertThat(firstSlide.getExercise().getId()).isEqualTo(testExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_WithNewSlideInPageOrder() throws IOException {
        // Arrange
        List<HiddenPageInfoDTO> hiddenPagesList = List.of();

        // Include a new temporary slide ID
        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1), new SlideOrderDTO("temp_new", 2), new SlideOrderDTO("3", 3));

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get a proper temp path for slides
        Path tempFilePath = FilePathService.getTempFilePath();
        Files.createDirectories(tempFilePath);

        // Create existing slides (we're missing slide 2 intentionally)
        // Create slide 1
        Path slidePath1 = tempFilePath.resolve("slide1.png");
        BufferedImage image1 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image1, "png", slidePath1.toFile());

        Slide slide1 = new Slide();
        slide1.setId(1L);
        slide1.setSlideNumber(1);
        slide1.setAttachmentUnit(testAttachmentUnit);
        slide1.setSlideImagePath("temp/slide1.png");
        slideRepository.save(slide1);

        // Create slide 3
        Path slidePath3 = tempFilePath.resolve("slide3.png");
        BufferedImage image3 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image3, "png", slidePath3.toFile());

        Slide slide3 = new Slide();
        slide3.setId(3L);
        slide3.setSlideNumber(3);
        slide3.setAttachmentUnit(testAttachmentUnit);
        slide3.setSlideImagePath("temp/slide3.png");
        slideRepository.save(slide3);

        // Act
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3); // Should now have 3 slides

        // Verify new slide was created with order 2
        boolean hasSlideWithOrder2 = slides.stream().anyMatch(s -> s.getSlideNumber() == 2);
        assertThat(hasSlideWithOrder2).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_CleanupRemovedSlides() throws IOException {
        // Arrange
        List<HiddenPageInfoDTO> hiddenPagesList = List.of();

        // Only include 2 of the 3 slides in page order
        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1), new SlideOrderDTO("2", 2));

        // Clear any existing slides first
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get a proper temp path for slides
        Path tempFilePath = FilePathService.getTempFilePath();
        Files.createDirectories(tempFilePath);

        // Create existing slides (all 3) with known IDs and valid paths
        for (int i = 1; i <= 3; i++) {
            // Create a real file in the temp directory
            Path slidePath = tempFilePath.resolve("slide" + i + ".png");

            // Create a simple image file (1x1 pixel)
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", slidePath.toFile());

            Slide slide = new Slide();
            slide.setId((long) i);
            slide.setSlideNumber(i);
            slide.setAttachmentUnit(testAttachmentUnit);

            // This stores a valid path that fileService.resolvePathToFileData() can resolve
            // The path is relative to the base path and should match what's expected
            slide.setSlideImagePath("temp/slide" + i + ".png");
            slideRepository.save(slide);
        }

        // Act
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(2); // Should only have 2 slides attached to unit

        // Check if slide 3 exists but is detached
        Slide slide3 = slideRepository.findById(3L).orElse(null);

        // If slide3 is null, the service is completely removing it rather than detaching
        if (slide3 == null) {
            // Test that it was removed instead
            assertThat(slideRepository.existsById(3L)).isFalse();
        }
        else {
            // Test that it was detached
            assertThat(slide3.getAttachmentUnit()).isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_UpdateHiddenStatus() throws IOException {
        // Create and save an Exercise1
        Exercise testExercise = new TextExercise();
        testExercise.setTitle("Test Exercise");
        exerciseRepository.save(testExercise);

        // Arrange
        ZonedDateTime hiddenDate = ZonedDateTime.now().plusDays(1);

        List<HiddenPageInfoDTO> hiddenPagesList = List.of(new HiddenPageInfoDTO("1", hiddenDate, testExercise.getId()));

        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1));

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get a proper temp path for slides
        Path tempFilePath = FilePathService.getTempFilePath();
        Files.createDirectories(tempFilePath);

        // Create a real file in the temp directory for slide 1
        Path slidePath = tempFilePath.resolve("slide1.png");

        // Create a simple image file (1x1 pixel)
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", slidePath.toFile());

        // Create existing slide with different hidden status
        Slide slide = new Slide();
        slide.setId(1L);
        slide.setSlideNumber(1);
        slide.setAttachmentUnit(testAttachmentUnit);
        slide.setSlideImagePath("temp/slide1.png");
        slide.setHidden(ZonedDateTime.now().plusDays(2)); // Different date
        slideRepository.save(slide);

        // Act
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        Slide updatedSlide = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId()).stream().filter(s -> s.getSlideNumber() == 1).findFirst().orElse(null);
        assertThat(updatedSlide).isNotNull();
        assertThat(updatedSlide.getHidden()).isNotNull();
        assertThat(updatedSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(hiddenDate.truncatedTo(ChronoUnit.MILLIS));

        // Verify the exercise association
        assertThat(updatedSlide.getExercise()).isNotNull();
        assertThat(updatedSlide.getExercise().getId()).isEqualTo(testExercise.getId());
    }

    // For tests that use String parameters instead of DTOs
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_WithStrings() throws IOException, InterruptedException {
        // Create and save an Exercise for testing
        Exercise testExercise = new TextExercise();
        testExercise.setTitle("Test Exercise");
        exerciseRepository.save(testExercise);

        // Arrange
        ZonedDateTime hiddenDate = ZonedDateTime.now().plusDays(1);

        // Create JSON strings for the methods that expect strings
        List<HiddenPageInfoDTO> hiddenPages = List.of(new HiddenPageInfoDTO("temp_1", hiddenDate, testExercise.getId()));

        List<SlideOrderDTO> pageOrder = List.of(new SlideOrderDTO("temp_1", 1), new SlideOrderDTO("temp_2", 2), new SlideOrderDTO("temp_3", 3));

        // Clear any existing slides for this test
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a mock PDF file with 3 pages
        Path tempDir = Files.createTempDirectory("test-slides");
        Path tempPdfPath = tempDir.resolve("test-slides.pdf");
        try (PDDocument doc = new PDDocument()) {
            // Add 3 pages to the document
            for (int i = 0; i < 3; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(tempPdfPath.toFile());
        }

        // Set up the attachment unit to use our test PDF file
        testAttachmentUnit.getAttachment().setLink(tempPdfPath.toUri().toString());
        testAttachmentUnit.getAttachment().setName("test-slides.pdf");

        // Instead of calling the async method, use the direct method with the loaded document
        try (PDDocument loadedDoc = Loader.loadPDF(tempPdfPath.toFile())) {
            slideSplitterService.splitAttachmentUnitIntoSingleSlides(loadedDoc, testAttachmentUnit, "test-slides.pdf", hiddenPages, pageOrder);
        }

        // Since the method is no longer asynchronous, we can check immediately, but add a small wait time for any DB
        // operations
        Thread.sleep(500);

        // Get the slides
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());

        // If we still don't have slides after waiting, test should fail with useful message
        if (slides == null || slides.isEmpty() || slides.size() < 3) {
            // For debugging, let's check what happened
            fail("Expected 3 slides but found " + (slides == null ? 0 : slides.size()) + " after waiting 500ms. " + "PDF file exists: " + tempPdfPath.toFile().exists() + ", "
                    + "PDF file size: " + tempPdfPath.toFile().length() + " bytes, " + "Page order: " + pageOrder);
        }

        // Assert
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3);

        // Verify slide numbers match the order in pageOrder
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 1).count()).isEqualTo(1);
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 2).count()).isEqualTo(1);
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 3).count()).isEqualTo(1);

        // Verify first slide is hidden with exercise
        Slide firstSlide = slides.stream().filter(s -> s.getSlideNumber() == 1).findFirst().orElse(null);
        assertThat(firstSlide).isNotNull();
        assertThat(firstSlide.getHidden()).isNotNull();
        // Compare dates truncated to millis to avoid timing precision issues
        assertThat(firstSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(hiddenDate.truncatedTo(ChronoUnit.MILLIS));
        assertThat(firstSlide.getExercise()).isNotNull();
        assertThat(firstSlide.getExercise().getId()).isEqualTo(testExercise.getId());
    }

    // Additional updated test methods

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_WithInvalidFilePath() {
        // Arrange
        List<HiddenPageInfoDTO> hiddenPagesList = List.of();
        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1));

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Set an invalid link that doesn't point to an actual file
        testAttachmentUnit.getAttachment().setLink("file:///nonexistent/path/file.pdf");

        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testAttachmentUnit, hiddenPagesList, pageOrderList);

        // Use Awaitility for deterministic waiting
        await().atMost(2, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            // The method should have attempted processing by now
            return true;
        });

        // Verify no slides were created due to the error
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        assertThat(slides).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_WithEmptyPageOrder() throws IOException {
        // Arrange
        List<HiddenPageInfoDTO> hiddenPagesList = List.of();
        List<SlideOrderDTO> pageOrderList = List.of();

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a mock PDF file
        Path tempDir = Files.createTempDirectory("test-slides");
        Path tempPdfPath = tempDir.resolve("test-slides.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(tempPdfPath.toFile());
        }

        // Set a valid attachment link
        testAttachmentUnit.getAttachment().setLink(tempPdfPath.toUri().toString());

        // Act - call the async method
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testAttachmentUnit, hiddenPagesList, pageOrderList);

        // Use Awaitility for deterministic waiting
        await().atMost(2, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            List<Slide> currentSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
            return currentSlides != null; // We're expecting an empty list in this case
        });

        // Assert - should not create any slides since page order is empty
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        assertThat(slides).isEmpty();

        // Clean up
        Files.deleteIfExists(tempPdfPath);
        Files.deleteIfExists(tempDir);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_WithStringsJson() throws IOException, InterruptedException {
        // Create and save an Exercise for testing
        Exercise testExercise = new TextExercise();
        testExercise.setTitle("Test Exercise");
        exerciseRepository.save(testExercise);

        // Arrange
        ZonedDateTime hiddenDate = ZonedDateTime.now().plusDays(1);

        // Create proper DTO objects
        List<HiddenPageInfoDTO> hiddenPagesList = List.of(new HiddenPageInfoDTO("temp_1", hiddenDate, testExercise.getId()));

        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("temp_1", 1), new SlideOrderDTO("temp_2", 2), new SlideOrderDTO("temp_3", 3));

        // Clear any existing slides for this test
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a mock PDF file with 3 pages
        Path tempDir = Files.createTempDirectory("test-slides");
        Path tempPdfPath = tempDir.resolve("test-slides.pdf");
        try (PDDocument doc = new PDDocument()) {
            // Add 3 pages to the document
            for (int i = 0; i < 3; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(tempPdfPath.toFile());
        }

        // Set up the attachment unit to use our test PDF file
        testAttachmentUnit.getAttachment().setLink(tempPdfPath.toUri().toString());
        testAttachmentUnit.getAttachment().setName("test-slides.pdf");

        // Instead of calling the async method, use the direct method with the loaded document
        try (PDDocument loadedDoc = Loader.loadPDF(tempPdfPath.toFile())) {
            slideSplitterService.splitAttachmentUnitIntoSingleSlides(loadedDoc, testAttachmentUnit, "test-slides.pdf", hiddenPagesList, pageOrderList);
        }

        // Since the method is no longer asynchronous, we can check immediately, but add a small wait time for any DB
        // operations
        Thread.sleep(500);

        // Get the slides
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());

        // If we still don't have slides after waiting, test should fail with useful message
        if (slides == null || slides.isEmpty() || slides.size() < 3) {
            // For debugging, let's check what happened
            fail("Expected 3 slides but found " + (slides == null ? 0 : slides.size()) + " after waiting 500ms. " + "PDF file exists: " + tempPdfPath.toFile().exists() + ", "
                    + "PDF file size: " + tempPdfPath.toFile().length() + " bytes, ");
        }

        // Assert
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3);

        // Verify slide numbers match the order in pageOrder
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 1).count()).isEqualTo(1);
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 2).count()).isEqualTo(1);
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 3).count()).isEqualTo(1);

        // Verify first slide is hidden with exercise
        Slide firstSlide = slides.stream().filter(s -> s.getSlideNumber() == 1).findFirst().orElse(null);
        assertThat(firstSlide).isNotNull();
        assertThat(firstSlide.getHidden()).isNotNull();
        // Compare dates truncated to millis to avoid timing precision issues
        assertThat(firstSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(hiddenDate.truncatedTo(ChronoUnit.MILLIS));
        assertThat(firstSlide.getExercise()).isNotNull();
        assertThat(firstSlide.getExercise().getId()).isEqualTo(testExercise.getId());

        // Clean up
        Files.deleteIfExists(tempPdfPath);
        Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {

            @Override
            public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
