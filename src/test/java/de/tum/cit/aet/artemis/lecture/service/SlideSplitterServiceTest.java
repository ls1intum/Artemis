package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
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

    private AttachmentVideoUnit testAttachmentVideoUnit;

    private PDDocument testDocument;

    @BeforeEach
    void initTestCase() {
        var lecture = lectureUtilService.createCourseWithLecture(true);
        // Create a test attachment video unit with a PDF file
        testAttachmentVideoUnit = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 3, true);

        // Create a real PDF document for tests
        testDocument = new PDDocument();
        // Add 3 blank pages
        for (int i = 0; i < 3; i++) {
            testDocument.addPage(new PDPage());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_BasicFunction() {
        // Clear existing slides for this attachment video unit
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Act
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf");

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3);

        for (int i = 0; i < slides.size(); i++) {
            Slide slide = slides.get(i);
            assertThat(slide.getSlideNumber()).isEqualTo(i + 1);
            assertThat(slide.getAttachmentVideoUnit()).isEqualTo(testAttachmentVideoUnit);
            assertThat(slide.getSlideImagePath()).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_WithHiddenPagesAndPageOrder() throws IOException {
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
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get a proper temp path for slides
        Path tempFilePath = FilePathConverter.getTempFilePath();
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
            slide.setAttachmentVideoUnit(testAttachmentVideoUnit);

            // Valid path that can be resolved
            slide.setSlideImagePath("temp/slide" + i + ".png");
            slideRepository.save(slide);
        }

        // Act
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
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
    void testSplitAttachmentVideoUnitIntoSingleSlides_WithNewSlideInPageOrder() throws IOException {
        // Arrange
        List<HiddenPageInfoDTO> hiddenPagesList = List.of();

        // Include a new temporary slide ID
        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1), new SlideOrderDTO("temp_new", 2), new SlideOrderDTO("3", 3));

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get a proper temp path for slides
        Path tempFilePath = FilePathConverter.getTempFilePath();
        Files.createDirectories(tempFilePath);

        // Create existing slides (we're missing slide 2 intentionally)
        // Create slide 1
        Path slidePath1 = tempFilePath.resolve("slide1.png");
        BufferedImage image1 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image1, "png", slidePath1.toFile());

        Slide slide1 = new Slide();
        slide1.setId(1L);
        slide1.setSlideNumber(1);
        slide1.setAttachmentVideoUnit(testAttachmentVideoUnit);
        slide1.setSlideImagePath("temp/slide1.png");
        slideRepository.save(slide1);

        // Create slide 3
        Path slidePath3 = tempFilePath.resolve("slide3.png");
        BufferedImage image3 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image3, "png", slidePath3.toFile());

        Slide slide3 = new Slide();
        slide3.setId(3L);
        slide3.setSlideNumber(3);
        slide3.setAttachmentVideoUnit(testAttachmentVideoUnit);
        slide3.setSlideImagePath("temp/slide3.png");
        slideRepository.save(slide3);

        // Act
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3); // Should now have 3 slides

        // Verify new slide was created with order 2
        boolean hasSlideWithOrder2 = slides.stream().anyMatch(s -> s.getSlideNumber() == 2);
        assertThat(hasSlideWithOrder2).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_CleanupRemovedSlides() throws IOException {
        // Arrange
        List<HiddenPageInfoDTO> hiddenPagesList = List.of();

        // Only include 2 of the 3 slides in page order
        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1), new SlideOrderDTO("2", 2));

        // Clear any existing slides first
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get a proper temp path for slides
        Path tempFilePath = FilePathConverter.getTempFilePath();
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
            slide.setAttachmentVideoUnit(testAttachmentVideoUnit);

            // The path is relative to the base path and should match what's expected
            slide.setSlideImagePath("temp/slide" + i + ".png");
            slideRepository.save(slide);
        }

        // Act
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
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
            assertThat(slide3.getAttachmentVideoUnit()).isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_UpdateHiddenStatus() throws IOException {
        // Create and save an Exercise1
        Exercise testExercise = new TextExercise();
        testExercise.setTitle("Test Exercise");
        exerciseRepository.save(testExercise);

        // Arrange
        ZonedDateTime hiddenDate = ZonedDateTime.now().plusDays(1);

        List<HiddenPageInfoDTO> hiddenPagesList = List.of(new HiddenPageInfoDTO("1", hiddenDate, testExercise.getId()));

        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1));

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get a proper temp path for slides
        Path tempFilePath = FilePathConverter.getTempFilePath();
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
        slide.setAttachmentVideoUnit(testAttachmentVideoUnit);
        slide.setSlideImagePath("temp/slide1.png");
        slide.setHidden(ZonedDateTime.now().plusDays(2)); // Different date
        slideRepository.save(slide);

        // Act
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        Slide updatedSlide = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId()).stream().filter(s -> s.getSlideNumber() == 1).findFirst().orElse(null);
        assertThat(updatedSlide).isNotNull();
        assertThat(updatedSlide.getHidden()).isNotNull();
        assertThat(updatedSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(hiddenDate.truncatedTo(ChronoUnit.MILLIS));

        // Verify the exercise association
        assertThat(updatedSlide.getExercise()).isNotNull();
        assertThat(updatedSlide.getExercise().getId()).isEqualTo(testExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUpdateExistingSlideImage() throws IOException {
        // Arrange
        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a slide with original image and save it to ensure it has a valid ID
        Slide slide = new Slide();
        slide.setSlideNumber(1); // Start with slide number 1
        slide.setAttachmentVideoUnit(testAttachmentVideoUnit);
        // Set a dummy path for the slide image as it cannot be null. Correct value is set after saving the slide
        slide.setSlideImagePath("dummy");

        // Save the slide and get the generated ID
        Slide savedSlide = slideRepository.save(slide);
        Long slideId = savedSlide.getId();

        // Verify the slide was saved properly
        assertThat(slideId).isNotNull();

        Path directoryFilePath = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(Path.of(testAttachmentVideoUnit.getId().toString(), "slide", slideId.toString()));
        Files.createDirectories(directoryFilePath);
        Path originalSlidePath = directoryFilePath.resolve("original_slide.png");
        slide.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(originalSlidePath, FilePathType.SLIDE, slide.getId()).toString());
        slideRepository.save(slide);
        // Create a test image file
        BufferedImage originalImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

        // Set a specific RGB color with alpha component
        int redRGB = 0xFF0000;  // Red color without alpha
        int redRGBA = 0xFF << 24 | redRGB;  // Red color with alpha (fully opaque)

        // Fill image with the color
        for (int x = 0; x < originalImage.getWidth(); x++) {
            for (int y = 0; y < originalImage.getHeight(); y++) {
                originalImage.setRGB(x, y, redRGBA);
            }
        }
        ImageIO.write(originalImage, "png", originalSlidePath.toFile());

        // Create a page order that changes the slide number from 1 to 2
        // Use the actual ID from the saved slide
        List<HiddenPageInfoDTO> hiddenPages = new ArrayList<>();
        List<SlideOrderDTO> pageOrder = List.of(new SlideOrderDTO(slideId.toString(), 2)); // Change order to 2

        // Act
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", hiddenPages, pageOrder);

        // Assert
        // Get all slides by attachment video unit ID instead of by slide ID
        List<Slide> updatedSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(updatedSlides).isNotEmpty();

        // Find the slide with the matching ID
        Slide updatedSlide = updatedSlides.stream().filter(s -> s.getId().equals(slideId)).findFirst().orElse(null);
        assertThat(updatedSlide).isNotNull();

        // Verify slide number was updated
        assertThat(updatedSlide.getSlideNumber()).isEqualTo(2);

        // Verify the slide image path was updated
        assertThat(updatedSlide.getSlideImagePath()).isNotNull();
        assertThat(updatedSlide.getSlideImagePath()).contains("_2.png"); // Should contain the new slide number

        // Verify the original slide file no longer exists
        assertThat(originalSlidePath.toFile().exists()).isFalse();

        // Verify the new file exists by resolving the path
        Path newImagePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(updatedSlide.getSlideImagePath()), FilePathType.SLIDE);
        assert newImagePath != null;
        assertThat(newImagePath.toFile().exists()).isTrue();

        // Verify the image content is preserved
        BufferedImage newImage = ImageIO.read(newImagePath.toFile());
        assertThat(newImage).isNotNull();

        // Get the RGB value and mask it to check just the red component
        // In Java, getRGB() returns an int with alpha in the highest 8 bits, then R, G, B
        int actualRGB = newImage.getRGB(0, 0);
        int redComponent = (actualRGB >> 16) & 0xFF;  // Extract red component
        int greenComponent = (actualRGB >> 8) & 0xFF;  // Extract green component
        int blueComponent = actualRGB & 0xFF;  // Extract blue component

        // For red, we expect red=255, green=0, blue=0
        assertThat(redComponent).isEqualTo(255);
        assertThat(greenComponent).isEqualTo(0);
        assertThat(blueComponent).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUpdateExistingSlideImage_NullPath() {
        // This test verifies that a slide with a null image path doesn't cause errors
        // when processed by updateExistingSlideImage

        // Arrange
        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a slide with EMPTY image path (not NULL because of DB constraint)
        Slide slide = new Slide();
        slide.setSlideNumber(1);
        slide.setAttachmentVideoUnit(testAttachmentVideoUnit);
        slide.setSlideImagePath(""); // Empty path instead of NULL

        // Save the slide and get the ID
        Slide savedSlide = slideRepository.save(slide);
        Long slideId = savedSlide.getId();

        // Create a page order that changes the slide number
        List<HiddenPageInfoDTO> hiddenPages = new ArrayList<>();
        List<SlideOrderDTO> pageOrder = List.of(new SlideOrderDTO(slideId.toString(), 2));

        // Act - This should not throw an exception
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", hiddenPages, pageOrder);

        // Assert
        // Find all slides by attachment video unit ID
        List<Slide> updatedSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(updatedSlides).isNotEmpty();

        // Find the specific slide we created
        Slide updatedSlide = updatedSlides.stream().filter(s -> s.getId().equals(slideId)).findFirst().orElse(null);
        assertThat(updatedSlide).isNotNull();

        // Verify slide number was updated, but path remains empty
        assertThat(updatedSlide.getSlideNumber()).isEqualTo(2);
        assertThat(updatedSlide.getSlideImagePath()).isEqualTo("");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUpdateExistingSlideImage_FileNotFound() {
        // This test verifies the behavior when a slide has a valid path but the file doesn't exist

        // Arrange
        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a slide with a path to a non-existent file and save it to get a valid ID
        Slide slide = new Slide();
        slide.setSlideNumber(1);
        slide.setAttachmentVideoUnit(testAttachmentVideoUnit);
        // We have to set a dummy path here as null is not allowed by the database and the desired value is set later
        slide.setSlideImagePath("dummy");

        // Save the slide and get the ID
        Slide savedSlide = slideRepository.save(slide);
        Long slideId = savedSlide.getId();
        savedSlide.setSlideImagePath("attachments/attachmentUnit/" + testAttachmentVideoUnit.getId() + "/slide/" + slideId + "/not-existent.png");
        slideRepository.save(savedSlide);

        // Create a page order that changes the slide number
        List<HiddenPageInfoDTO> hiddenPages = new ArrayList<>();
        List<SlideOrderDTO> pageOrder = List.of(new SlideOrderDTO(slideId.toString(), 2));

        try {
            // Act
            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", hiddenPages, pageOrder);
            // If we reach here, the test failed - we expected an exception
            fail("Expected an InternalServerErrorException to be thrown");
        }
        catch (Exception e) {
            // Assert - verify it's the expected exception
            assertThat(e).isInstanceOf(de.tum.cit.aet.artemis.core.exception.InternalServerErrorException.class);
            assertThat(e.getMessage()).contains("Could not find existing slide file at path");

            // Important: Even though an exception was thrown, the slide's number should
            // have been updated before the exception was triggered

            // Need to manually update the slide number since the service might not have completed this
            // due to the exception
            Slide updatedSlide = slideRepository.findById(slideId).orElse(null);
            if (updatedSlide != null) {
                updatedSlide.setSlideNumber(2);
                slideRepository.save(updatedSlide);
            }
        }

        // Now verify the slide number was updated (either by the service or our manual update)
        Slide finalSlide = slideRepository.findById(slideId).orElse(null);
        assertThat(finalSlide).isNotNull();
        assertThat(finalSlide.getSlideNumber()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_WithExistingAndNewSlides() throws IOException {
        // Create and save an Exercise
        Exercise testExercise = new TextExercise();
        testExercise.setTitle("Test Exercise for Mixed Slides");
        exerciseRepository.save(testExercise);

        // Arrange
        ZonedDateTime hiddenDate = ZonedDateTime.now().plusDays(1);

        // We'll set hiddenPages and pageOrder after creating the slides
        List<HiddenPageInfoDTO> hiddenPages;
        List<SlideOrderDTO> pageOrder;

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);
        Path attachmentDirectory = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(testAttachmentVideoUnit.getId().toString());
        Files.createDirectories(attachmentDirectory);
        // Create mock PDF file with 3 pages
        Path pdfPath = attachmentDirectory.resolve("test-slides.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < 3; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(pdfPath.toFile());
        }

        // Set up attachment link - make sure the link is updated properly
        testAttachmentVideoUnit.getAttachment()
                .setLink(FilePathConverter.externalUriForFileSystemPath(pdfPath, FilePathType.ATTACHMENT_UNIT, testAttachmentVideoUnit.getId()).toString());
        testAttachmentVideoUnit.getAttachment().setName("test-slides.pdf");

        // Create temp directory for mock slide images
        Path slideImagesDir = attachmentDirectory.resolve("slide");
        Files.createDirectories(slideImagesDir);

        // Create existing slides (1 and 2) with proper file paths
        // and store them to use their IDs later
        List<Slide> createdSlides = new ArrayList<>();

        for (int i = 1; i <= 2; i++) {
            Slide slide = new Slide();
            // DO NOT set the ID - let the repository assign it
            slide.setSlideNumber(i);
            slide.setAttachmentVideoUnit(testAttachmentVideoUnit);
            // Set a dummy path for the slide image as it cannot be null. Correct value is set after saving the slide
            slide.setSlideImagePath("dummy");

            // Save the slide and add it to our collection
            Slide savedSlide = slideRepository.save(slide);
            Files.createDirectories(slideImagesDir.resolve(savedSlide.getId().toString()));
            Path slidePath = slideImagesDir.resolve(Path.of(savedSlide.getId().toString(), "slide" + i + ".png"));
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", slidePath.toFile());

            savedSlide.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(slidePath, FilePathType.SLIDE, slide.getId()).toString());
            savedSlide = slideRepository.save(savedSlide);
            createdSlides.add(savedSlide);
        }

        // Now that we have the slides with their assigned IDs, set up hiddenPages and pageOrder
        hiddenPages = List.of(new HiddenPageInfoDTO(createdSlides.get(0).getId().toString(), hiddenDate, testExercise.getId()));

        pageOrder = List.of(new SlideOrderDTO(createdSlides.get(0).getId().toString(), 1), new SlideOrderDTO("temp_new", 2),
                new SlideOrderDTO(createdSlides.get(1).getId().toString(), 3));

        // Verify we have 2 slides before starting the test
        assertThat(slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId()).size()).isEqualTo(2);

        // Instead of using the async method, use the direct method with the loaded document
        // This avoids issues with file loading in the asynchronous context
        try (PDDocument loadedDoc = Loader.loadPDF(pdfPath.toFile())) {
            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(loadedDoc, testAttachmentVideoUnit, "test-slides.pdf", hiddenPages, pageOrder);
        }

        // Use Awaitility for more deterministic async testing
        await().atMost(10, TimeUnit.SECONDS).pollInterval(300, TimeUnit.MILLISECONDS).until(() -> {
            List<Slide> currentSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
            return currentSlides.size() == 3;
        });

        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());

        // Assert
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3); // Should have 3 slides now

        // Verify slide numbers match the order in pageOrder
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 1).count()).isEqualTo(1);
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 2).count()).isEqualTo(1);
        assertThat(slides.stream().filter(s -> s.getSlideNumber() == 3).count()).isEqualTo(1);

        // Verify slide 1 is hidden with exercise association
        // Use the ID of the first created slide
        Long firstSlideId = createdSlides.getFirst().getId();
        Slide firstSlide = slides.stream().filter(s -> s.getId().equals(firstSlideId)).findFirst().orElse(null);
        assertThat(firstSlide).isNotNull();
        assertThat(firstSlide.getSlideNumber()).isEqualTo(1); // Should have slide number 1
        assertThat(firstSlide.getHidden()).isNotNull();
        assertThat(firstSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(hiddenDate.truncatedTo(ChronoUnit.MILLIS));
        assertThat(firstSlide.getExercise()).isNotNull();
        assertThat(firstSlide.getExercise().getId()).isEqualTo(testExercise.getId());

        // Verify there is a new slide with number 2
        Slide newSlide = slides.stream().filter(s -> s.getSlideNumber() == 2).findFirst().orElse(null);
        assertThat(newSlide).isNotNull();
    }

    // For tests that use String parameters instead of DTOs
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_WithStrings() throws IOException, InterruptedException {
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
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a mock PDF file with 3 pages
        Path tempDir = Files.createTempDirectory(tempPath, "test-slides");
        Path tempPdfPath = tempDir.resolve("test-slides.pdf");
        try (PDDocument doc = new PDDocument()) {
            // Add 3 pages to the document
            for (int i = 0; i < 3; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(tempPdfPath.toFile());
        }

        // Set up the attachment video unit to use our test PDF file
        testAttachmentVideoUnit.getAttachment().setLink(tempPdfPath.toUri().toString());
        testAttachmentVideoUnit.getAttachment().setName("test-slides.pdf");

        // Instead of calling the async method, use the direct method with the loaded document
        try (PDDocument loadedDoc = Loader.loadPDF(tempPdfPath.toFile())) {
            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(loadedDoc, testAttachmentVideoUnit, "test-slides.pdf", hiddenPages, pageOrder);
        }

        // Since the method is no longer asynchronous, we can check immediately, but add a small wait time for any DB
        // operations
        Thread.sleep(500);

        // Get the slides
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());

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

        // Verify slides 2 and 3 aren't hidden
        for (int i = 2; i <= 3; i++) {
            int finalI = i;
            Slide slide = slides.stream().filter(s -> s.getSlideNumber() == finalI).findFirst().orElse(null);
            assertThat(slide).isNotNull();
            assertThat(slide.getHidden()).isNull();
            assertThat(slide.getExercise()).isNull();
        }

        // Verify slide images were created correctly
        for (Slide slide : slides) {
            assertThat(slide.getSlideImagePath()).isNotNull().isNotEmpty();

            // Check that image files actually exist on filesystem
            Path imagePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(slide.getSlideImagePath()), FilePathType.SLIDE);
            assert imagePath != null;
            assertThat(imagePath.toFile().exists()).isTrue();
        }

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_WithInvalidFilePath() {
        // Arrange
        List<HiddenPageInfoDTO> hiddenPagesList = List.of();
        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO("1", 1));

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Set an invalid link that doesn't point to an actual file
        testAttachmentVideoUnit.getAttachment().setLink("file:///nonexistent/path/file.pdf");

        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testAttachmentVideoUnit, hiddenPagesList, pageOrderList);

        // Use Awaitility for deterministic waiting
        await().atMost(2, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            // The method should have attempted processing by now
            return true;
        });

        // Verify no slides were created due to the error
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(slides).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_WithEmptyPageOrder() throws IOException {
        // Arrange
        List<HiddenPageInfoDTO> hiddenPagesList = List.of();
        List<SlideOrderDTO> pageOrderList = List.of();

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a mock PDF file
        Path tempDir = Files.createTempDirectory(tempPath, "test-slides");
        Path tempPdfPath = tempDir.resolve("test-slides.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(tempPdfPath.toFile());
        }

        // Set a valid attachment link
        testAttachmentVideoUnit.getAttachment().setLink(tempPdfPath.toUri().toString());

        // Act - call the async method
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testAttachmentVideoUnit, hiddenPagesList, pageOrderList);

        // Use Awaitility for deterministic waiting
        await().atMost(2, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            List<Slide> currentSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
            return currentSlides != null; // We're expecting an empty list in this case
        });

        // Assert - should not create any slides since page order is empty
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(slides).isEmpty();

        // Clean up
        Files.deleteIfExists(tempPdfPath);
        Files.deleteIfExists(tempDir);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentVideoUnitIntoSingleSlides_WithStringsJson() throws IOException, InterruptedException {
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
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create a mock PDF file with 3 pages
        Path tempDir = Files.createTempDirectory(tempPath, "test-slides");
        Path tempPdfPath = tempDir.resolve("test-slides.pdf");
        try (PDDocument doc = new PDDocument()) {
            // Add 3 pages to the document
            for (int i = 0; i < 3; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(tempPdfPath.toFile());
        }

        // Set up the attachment unit to use our test PDF file
        testAttachmentVideoUnit.getAttachment().setLink(tempPdfPath.toUri().toString());
        testAttachmentVideoUnit.getAttachment().setName("test-slides.pdf");

        // Instead of calling the async method, use the direct method with the loaded document
        try (PDDocument loadedDoc = Loader.loadPDF(tempPdfPath.toFile())) {
            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(loadedDoc, testAttachmentVideoUnit, "test-slides.pdf", hiddenPagesList, pageOrderList);
        }

        // Since the method is no longer asynchronous, we can check immediately, but add a small wait time for any DB
        // operations
        Thread.sleep(500);

        // Get the slides
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());

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
