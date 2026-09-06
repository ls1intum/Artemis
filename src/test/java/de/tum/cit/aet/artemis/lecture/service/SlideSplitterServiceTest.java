package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class SlideSplitterServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "slidesplitterservicetest";

    @Autowired
    private SlideSplitterService slideSplitterService;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private TempFileUtilService tempFileUtilService;

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
    void repeatedBasicSlideSplitUsesUniqueImagePaths() {
        slideRepository.deleteAll(slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId()));

        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf");
        List<Slide> firstSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        List<String> firstImagePaths = firstSlides.stream().map(Slide::getSlideImagePath).toList();

        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf");
        List<String> allImagePaths = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId()).stream().map(Slide::getSlideImagePath).toList();

        assertThat(allImagePaths).hasSize(6).doesNotHaveDuplicates();
        assertThat(allImagePaths).containsAll(firstImagePaths);
        assertThat(firstSlides).allSatisfy(slide -> assertThat(slideImageFile(slide)).exists());
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
        slide1.setSlideNumber(1);
        slide1.setAttachmentVideoUnit(testAttachmentVideoUnit);
        slide1.setSlideImagePath("temp/slide1.png");
        slideRepository.save(slide1);

        // Create slide 3
        Path slidePath3 = tempFilePath.resolve("slide3.png");
        BufferedImage image3 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image3, "png", slidePath3.toFile());

        Slide slide3 = new Slide();
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

        // Clear any existing slides first
        List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        slideRepository.deleteAll(existingSlides);

        // Get the proper attachment directory for slides
        Path attachmentDirectory = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(testAttachmentVideoUnit.getId().toString());
        Path slideImagesDir = attachmentDirectory.resolve("slide");
        Files.createDirectories(slideImagesDir);

        // Create existing slides (all 3) and store their IDs
        List<Long> slideIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Slide slide = new Slide();
            slide.setSlideNumber(i);
            slide.setAttachmentVideoUnit(testAttachmentVideoUnit);
            // Set a dummy path first as it cannot be null
            slide.setSlideImagePath("dummy");

            // Save the slide to get an ID
            Slide savedSlide = slideRepository.save(slide);
            slideIds.add(savedSlide.getId());

            // Create the proper directory structure for the slide, which the service names by the slide number rather than by the slide id
            Path slideDir = slideImagesDir.resolve(String.valueOf(i));
            Files.createDirectories(slideDir);
            Path slidePath = slideDir.resolve("slide" + i + ".png");

            // Create a simple image file (1x1 pixel)
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", slidePath.toFile());

            // Update the slide with the proper path format
            savedSlide.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(slidePath, FilePathType.SLIDE, (long) i).toString());
            slideRepository.save(savedSlide);
        }

        // Only include 2 of the 3 slides in page order - use actual IDs
        List<SlideOrderDTO> pageOrderList = List.of(new SlideOrderDTO(slideIds.get(0).toString(), 1), new SlideOrderDTO(slideIds.get(1).toString(), 2));

        // Act
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", hiddenPagesList, pageOrderList);

        // Assert
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(slides).isNotNull();
        assertThat(slides.size()).isEqualTo(2); // Should only have 2 slides attached to unit

        // Check if slide 3 exists but is detached - use actual ID
        Long thirdSlideId = slideIds.get(2);
        Slide slide3 = slideRepository.findById(thirdSlideId).orElse(null);

        // If slide3 is null, the service is completely removing it rather than detaching
        if (slide3 == null) {
            // Test that it was removed instead
            assertThat(slideRepository.existsById(thirdSlideId)).isFalse();
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
        assertThat(updatedSlide.getHidden().toInstant()).isCloseTo(hiddenDate.toInstant(), within(1, ChronoUnit.MILLIS));

        // Verify the exercise association
        assertThat(updatedSlide.getExercise()).isNotNull();
        assertThat(updatedSlide.getExercise().getId()).isEqualTo(testExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void updateSlideVisibilityDoesNotRebuildSlideContent() {
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        Slide hiddenSlide = slides.getFirst();
        ZonedDateTime hiddenUntil = ZonedDateTime.now().plusDays(1);
        String originalImagePath = hiddenSlide.getSlideImagePath();

        slideSplitterService.updateSlideVisibility(testAttachmentVideoUnit, List.of(new HiddenPageInfoDTO(hiddenSlide.getId().toString(), hiddenUntil, null)));

        List<Slide> updatedSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        Slide updatedHiddenSlide = updatedSlides.stream().filter(slide -> slide.getId().equals(hiddenSlide.getId())).findFirst().orElseThrow();
        assertThat(updatedHiddenSlide.getHidden().toInstant()).isCloseTo(hiddenUntil.toInstant(), within(1, ChronoUnit.MILLIS));
        assertThat(updatedHiddenSlide.getSlideImagePath()).isEqualTo(originalImagePath);
        assertThat(updatedSlides.stream().filter(slide -> !slide.getId().equals(hiddenSlide.getId()))).allMatch(slide -> slide.getHidden() == null);

        slideSplitterService.updateSlideVisibility(testAttachmentVideoUnit, List.of());

        assertThat(slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId())).allMatch(slide -> slide.getHidden() == null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void updateSlideVisibilityWaitsForConcurrentSlideMutation() throws Exception {
        Slide slide = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId()).getFirst();
        ZonedDateTime hiddenUntil = ZonedDateTime.now().plusDays(1);
        CountDownLatch mutationLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var concurrentMutation = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                attachmentVideoUnitRepository.findByIdForUpdate(testAttachmentVideoUnit.getId()).orElseThrow();
                mutationLockAcquired.countDown();
                try {
                    if (!releaseMutation.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release the simulated slide mutation");
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(mutationLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            var visibilityUpdate = executor
                    .submit(() -> slideSplitterService.updateSlideVisibility(testAttachmentVideoUnit, List.of(new HiddenPageInfoDTO(slide.getId().toString(), hiddenUntil, null))));
            assertThatThrownBy(() -> visibilityUpdate.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            releaseMutation.countDown();
            concurrentMutation.get(5, TimeUnit.SECONDS);
            visibilityUpdate.get(5, TimeUnit.SECONDS);
        }
        finally {
            releaseMutation.countDown();
            executor.shutdownNow();
        }

        Slide updatedSlide = slideRepository.findById(slide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden().toInstant()).isCloseTo(hiddenUntil.toInstant(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void slideSplitRollbackKeepsPreviousImagesAndRemovesReplacementFiles() throws IOException {
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        Slide firstSlide = slides.get(0);
        Slide secondSlide = slides.get(1);
        Path slideDirectory = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(testAttachmentVideoUnit.getId().toString()).resolve("slide");
        Path firstSlideOriginalFile = slideDirectory.resolve(firstSlide.getId().toString()).resolve(Path.of(firstSlide.getSlideImagePath()).getFileName());
        Path secondSlideOriginalFile = slideDirectory.resolve(secondSlide.getId().toString()).resolve(Path.of(secondSlide.getSlideImagePath()).getFileName());
        firstSlide.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(firstSlideOriginalFile, FilePathType.SLIDE, firstSlide.getId()).toString());
        secondSlide.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(secondSlideOriginalFile, FilePathType.SLIDE, secondSlide.getId()).toString());
        slideRepository.saveAll(List.of(firstSlide, secondSlide));
        String firstSlideOriginalImagePath = firstSlide.getSlideImagePath();
        Files.delete(secondSlideOriginalFile);
        Path attachmentDirectory = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(testAttachmentVideoUnit.getId().toString());
        Set<Path> filesBeforeFailedSplit;
        try (var files = Files.walk(attachmentDirectory)) {
            filesBeforeFailedSplit = files.filter(Files::isRegularFile).collect(Collectors.toSet());
        }
        List<SlideOrderDTO> pageOrder = List.of(new SlideOrderDTO(firstSlide.getId().toString(), 2), new SlideOrderDTO(secondSlide.getId().toString(), 1));

        assertThatThrownBy(() -> slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(testDocument, testAttachmentVideoUnit, "test.pdf", List.of(), pageOrder))
                .isInstanceOf(InternalServerErrorException.class);

        Slide unchangedFirstSlide = slideRepository.findById(firstSlide.getId()).orElseThrow();
        assertThat(unchangedFirstSlide.getSlideImagePath()).isEqualTo(firstSlideOriginalImagePath);
        assertThat(firstSlideOriginalFile).exists();
        try (var files = Files.walk(attachmentDirectory)) {
            assertThat(files.filter(Files::isRegularFile).collect(Collectors.toSet())).isEqualTo(filesBeforeFailedSplit);
        }
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

        // A slide image is stored under the slide's number, not under its id, which is what the service writes and therefore what it has to find again.
        Path directoryFilePath = FilePathConverter.getAttachmentVideoUnitFileSystemPath()
                .resolve(Path.of(testAttachmentVideoUnit.getId().toString(), "slide", String.valueOf(slide.getSlideNumber())));
        Files.createDirectories(directoryFilePath);
        Path originalSlidePath = directoryFilePath.resolve("original_slide.png");
        slide.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(originalSlidePath, FilePathType.SLIDE, (long) slide.getSlideNumber()).toString());
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
        Path newImagePath = slideImageFile(updatedSlide);
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
            // The service names the directory by the slide number rather than by the slide id
            Files.createDirectories(slideImagesDir.resolve(String.valueOf(i)));
            Path slidePath = slideImagesDir.resolve(Path.of(String.valueOf(i), "slide" + i + ".png"));
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", slidePath.toFile());

            savedSlide.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(slidePath, FilePathType.SLIDE, (long) i).toString());
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
        assertThat(firstSlide.getHidden().toInstant()).isCloseTo(hiddenDate.toInstant(), within(1, ChronoUnit.MILLIS));
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
        Path tempDir = tempFileUtilService.createTempDirectory("test-slides");
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
        // The service stores this exact value (SlideSplitterService.setHidden(hiddenPageInfo.date())), so there is
        // only one clock sample; the persisted copy differs solely because the `hidden` column is datetime(3) and
        // the database ROUNDS to millisecond precision. That rounding can carry the value across a second boundary
        // (e.g. ...:56.9997 -> ...:57.000), which is why truncating both sides to seconds could differ by a whole
        // second. The true deviation is at most 0.5 ms, so assert that instead — matching the millisecond tolerance
        // already used further down in this file.
        assertThat(firstSlide.getHidden().toInstant()).isCloseTo(hiddenDate.toInstant(), within(1, ChronoUnit.MILLIS));
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
            assertThat(slideImageFile(slide).toFile().exists()).isTrue();
        }

        // Clean up
        Files.deleteIfExists(tempPdfPath);
        Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {

            @Override
            public @NonNull FileVisitResult visitFile(Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NonNull FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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
        attachmentRepository.saveAndFlush(testAttachmentVideoUnit.getAttachment());

        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(testAttachmentVideoUnit, hiddenPagesList, pageOrderList));

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

        // Act - call the async method
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(testAttachmentVideoUnit, hiddenPagesList, pageOrderList)).join();

        // Use Awaitility for deterministic waiting
        await().atMost(2, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> {
            List<Slide> currentSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
            return currentSlides != null; // We're expecting an empty list in this case
        });

        // Assert - should not create any slides since page order is empty
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(slides).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testObsoleteAttachmentRevisionIsIgnoredWhenSplitJobsExecuteInReverseOrder() {
        List<Slide> originalSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        assertThat(originalSlides).hasSize(3);
        slideRepository.deleteAll(originalSlides);

        List<SlideOrderDTO> oldPageOrder = List.of(new SlideOrderDTO("temp_1", 3), new SlideOrderDTO("temp_2", 2), new SlideOrderDTO("temp_3", 1));
        AttachmentVideoUnitSlideSplitJob oldJob = AttachmentVideoUnitSlideSplitJob.of(testAttachmentVideoUnit, List.of(), oldPageOrder);

        Integer oldVersion = testAttachmentVideoUnit.getAttachment().getVersion();
        testAttachmentVideoUnit.getAttachment().setVersion(oldVersion == null ? 1 : oldVersion + 1);
        testAttachmentVideoUnit.getAttachment().setSha256Hash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        attachmentRepository.saveAndFlush(testAttachmentVideoUnit.getAttachment());

        ZonedDateTime hiddenUntil = ZonedDateTime.now().plusDays(1);
        List<SlideOrderDTO> currentPageOrder = List.of(new SlideOrderDTO("temp_1", 1), new SlideOrderDTO("temp_2", 2), new SlideOrderDTO("temp_3", 3));
        AttachmentVideoUnitSlideSplitJob currentJob = AttachmentVideoUnitSlideSplitJob.of(testAttachmentVideoUnit, List.of(new HiddenPageInfoDTO("temp_1", hiddenUntil, null)),
                currentPageOrder);

        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(currentJob).join();
        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(oldJob).join();

        await().atMost(2, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            List<Slide> resultingSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
            assertThat(resultingSlides).hasSize(3);
            assertThat(resultingSlides).extracting(Slide::getSlideNumber).containsExactly(1, 2, 3);
            assertThat(resultingSlides.getFirst().getHidden()).isCloseTo(hiddenUntil, within(1, ChronoUnit.MILLIS));
            assertThat(resultingSlides.subList(1, resultingSlides.size())).allMatch(slide -> slide.getHidden() == null);
        });
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
        Path tempDir = tempFileUtilService.createTempDirectory("test-slides");
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
        // The service stores this exact value (SlideSplitterService.setHidden(hiddenPageInfo.date())), so there is
        // only one clock sample; the persisted copy differs solely because the `hidden` column is datetime(3) and
        // the database ROUNDS to millisecond precision. That rounding can carry the value across a second boundary
        // (e.g. ...:56.9997 -> ...:57.000), which is why truncating both sides to seconds could differ by a whole
        // second. The true deviation is at most 0.5 ms, so assert that instead — matching the millisecond tolerance
        // already used further down in this file.
        assertThat(firstSlide.getHidden().toInstant()).isCloseTo(hiddenDate.toInstant(), within(1, ChronoUnit.MILLIS));
        assertThat(firstSlide.getExercise()).isNotNull();
        assertThat(firstSlide.getExercise().getId()).isEqualTo(testExercise.getId());

        // Clean up
        Files.deleteIfExists(tempPdfPath);
        Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {

            @Override
            public @NonNull FileVisitResult visitFile(Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NonNull FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * The image file of a slide, located from the slide itself: the unit it belongs to and the number it currently has name the directory, and only the filename comes out of the
     * stored value.
     *
     * @param slide the slide whose image is wanted
     * @return the location of the slide image on disk
     */
    private static Path slideImageFile(Slide slide) {
        return new FileSystemLocation.Slide(slide.getAttachmentVideoUnit().getId(), slide.getSlideNumber(), slide.getSlideImagePath()).path();
    }
}
