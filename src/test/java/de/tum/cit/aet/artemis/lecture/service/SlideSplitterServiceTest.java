package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.imageio.ImageIO;

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
        // Arrange
        String hiddenPages = "[{\"slideId\":\"1\",\"date\":\"" + ZonedDateTime.now().plusDays(1).toString() + "\",\"exerciseId\":1}]";

        String pageOrder = "[{\"slideId\":\"1\",\"order\":1},{\"slideId\":\"2\",\"order\":2},{\"slideId\":\"3\",\"order\":3}]";

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create and save an Exercise with ID 1
        Exercise testExercise = new TextExercise();
        testExercise.setId(1L);
        testExercise.setTitle("Test Exercise");
        exerciseRepository.save(testExercise);

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
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf", hiddenPages, pageOrder);

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(3);

        // First slide should be hidden with exercise
        Slide firstSlide = slides.stream().filter(s -> s.getSlideNumber() == 1).findFirst().orElse(null);
        assertThat(firstSlide).isNotNull();
        assertThat(firstSlide.getHidden()).isNotNull();
        assertThat(firstSlide.getExercise()).isNotNull();
        assertThat(firstSlide.getExercise().getId()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testSplitAttachmentUnitIntoSingleSlides_WithNewSlideInPageOrder() throws IOException {
        // Arrange
        String hiddenPages = "[]";

        // Include a new temporary slide ID
        String pageOrder = "[{\"slideId\":\"1\",\"order\":1},{\"slideId\":\"temp_new\",\"order\":2},{\"slideId\":\"3\",\"order\":3}]";

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
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf", hiddenPages, pageOrder);

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
        String hiddenPages = "[]";

        // Only include 2 of the 3 slides in page order
        String pageOrder = "[{\"slideId\":\"1\",\"order\":1},{\"slideId\":\"2\",\"order\":2}]";

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
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf", hiddenPages, pageOrder);

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
        // Arrange
        ZonedDateTime hiddenDate = ZonedDateTime.now().plusDays(1);
        String hiddenPages = "[{\"slideId\":\"1\",\"date\":\"" + hiddenDate.toString() + "\",\"exerciseId\":1}]";

        String pageOrder = "[{\"slideId\":\"1\",\"order\":1}]";

        // Clear existing slides
        List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Create and save an Exercise with ID 1
        Exercise testExercise = new TextExercise();
        testExercise.setId(1L);
        testExercise.setTitle("Test Exercise");
        exerciseRepository.save(testExercise);

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
        slideSplitterService.splitAttachmentUnitIntoSingleSlides(testDocument, testAttachmentUnit, "test.pdf", hiddenPages, pageOrder);

        // Assert
        Slide updatedSlide = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId()).stream().filter(s -> s.getSlideNumber() == 1).findFirst().orElse(null);
        assertThat(updatedSlide).isNotNull();
        assertThat(updatedSlide.getHidden()).isNotNull();
        assertThat(updatedSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(hiddenDate.truncatedTo(ChronoUnit.MILLIS));

        // Verify the exercise association
        assertThat(updatedSlide.getExercise()).isNotNull();
        assertThat(updatedSlide.getExercise().getId()).isEqualTo(1L);
    }
}
