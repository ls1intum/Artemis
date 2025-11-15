package de.tum.cit.aet.artemis.lecture.util;

import static org.assertj.core.api.Assertions.fail;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.OnlineUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to lectures for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class LectureUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private LectureTestRepository lectureRepo;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private OnlineUnitRepository onlineUnitRepository;

    @Autowired
    private ConversationTestRepository conversationRepository;

    @Autowired
    private LectureUnitCompletionRepository lectureUnitCompletionRepository;

    /**
     * Creates and saves a Course with a Lecture. The Lecture is only saved optionally. The Lecture is empty as it does not contain any LectureUnits.
     *
     * @param saveLecture True, if the Lecture should be saved
     * @return The created Lecture
     */
    public Lecture createCourseWithLecture(boolean saveLecture) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        courseRepo.save(course);
        if (saveLecture) {
            lectureRepo.save(lecture);
        }
        return lecture;
    }

    /**
     * Creates and saves a Lecture for the given Course. The Lecture is empty as it does not contain any LectureUnits.
     *
     * @param course      The Course the Lecture belongs to
     * @param visibleDate The visible date of the Lecture
     * @return The created Lecture
     */
    public Lecture createLecture(Course course, ZonedDateTime visibleDate) {
        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        lecture.setVisibleDate(visibleDate);
        lectureRepo.save(lecture);
        return lecture;
    }

    /**
     * Creates and saves a Lecture for the given Course. The Lecture is empty as it does not contain any LectureUnits.
     *
     * @param course      The Course the Lecture belongs to
     * @param visibleDate The visible date of the Lecture
     * @param startDate   The start date of the Lecture
     * @param endDate     The end date of the Lecture
     * @return The created Lecture
     */
    public Lecture createLecture(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Lecture lecture = new Lecture();
        lecture.setTitle("Test Lecture");
        lecture.setCourse(course);
        lecture.setVisibleDate(visibleDate);
        lecture.setStartDate(startDate);
        lecture.setEndDate(endDate);
        lectureRepo.save(lecture);
        return lecture;
    }

    /**
     * Adds the given Competencies to all LectureUnits of the given Lecture and saves the updated LectureUnits.
     *
     * @param lecture      The Lecture whose LectureUnits should be updated
     * @param competencies The Competencies to add to the LectureUnits
     * @return The Lecture with updated LectureUnits
     */
    public Lecture addCompetencyToLectureUnits(Lecture lecture, Set<CourseCompetency> competencies) {
        Lecture l = lectureRepo.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture.getId());
        l.getLectureUnits().forEach(lectureUnit -> {
            competencies.forEach(competency -> {
                CompetencyLectureUnitLink link = new CompetencyLectureUnitLink(competency, lectureUnit, 1);
                lectureUnit.getCompetencyLinks().add(link);
            });
            lectureUnitRepository.save(lectureUnit);
        });
        return l;
    }

    /**
     * Adds the given LectureUnits to the given Lecture and saves the updated Lecture.
     *
     * @param lecture      The Lecture to add the LectureUnits to
     * @param lectureUnits The LectureUnits to add to the Lecture
     * @return The updated Lecture
     */
    public Lecture addLectureUnitsToLecture(Lecture lecture, List<LectureUnit> lectureUnits) {
        Lecture existingLecture = lectureRepo.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        for (LectureUnit lectureUnit : lectureUnits) {
            if (!existingLecture.getLectureUnits().contains(lectureUnit)) {
                existingLecture.addLectureUnit(lectureUnit);
            }
        }
        return lectureRepo.save(existingLecture);
    }

    /**
     * Creates and saves a Channel for the given Lecture.
     *
     * @param lecture The Lecture the Channel belongs to
     * @return The created Channel
     */
    public Channel addLectureChannel(Lecture lecture) {
        Channel channel = ConversationFactory.generateCourseWideChannel(lecture.getCourse());
        channel.setLecture(lecture);
        return conversationRepository.save(channel);
    }

    /**
     * Creates and saves an ExerciseUnit for the given Exercise.
     *
     * @param exercise The Exercise the ExerciseUnit belongs to
     * @return The created ExerciseUnit
     */
    public ExerciseUnit createExerciseUnit(Exercise exercise, Lecture lecture) {
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        exerciseUnit.setLecture(lecture);
        return exerciseUnitRepository.save(exerciseUnit);
    }

    /**
     * Creates and saves an AttachmentVideoUnit without an Attachment.
     *
     * @return The created AttachmentVideoUnit
     */
    public AttachmentVideoUnit createAttachmentVideoUnitWithoutAttachment(Lecture lecture) {
        AttachmentVideoUnit attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setDescription("Lorem Ipsum");
        attachmentVideoUnit.setVideoSource("http://video.fake");
        attachmentVideoUnit.setLecture(lecture);
        return attachmentVideoUnitRepository.save(attachmentVideoUnit);
    }

    /**
     * Creates and saves an AttachmentVideoUnit with an Attachment. The Attachment can be created with or without a link to an image file.
     *
     * @param lecture  the Lecture the AttachmentVideoUnit belongs to
     * @param withFile True, if the Attachment should link to a file
     * @return The created AttachmentVideoUnit
     */
    public AttachmentVideoUnit createAttachmentVideoUnit(Lecture lecture, boolean withFile) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        AttachmentVideoUnit attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setDescription("Lorem Ipsum");
        attachmentVideoUnit.setLecture(lecture);
        attachmentVideoUnit = attachmentVideoUnitRepository.save(attachmentVideoUnit);
        Attachment attachment = withFile ? LectureFactory.generateAttachmentWithFile(started, attachmentVideoUnit.getId(), true) : LectureFactory.generateAttachment(started);
        attachment.setAttachmentVideoUnit(attachmentVideoUnit);
        attachment = attachmentRepository.save(attachment);
        attachmentVideoUnit.setAttachment(attachment);
        attachmentVideoUnit.setName(attachment.getName());
        attachmentVideoUnit.setReleaseDate(attachment.getReleaseDate());
        return attachmentVideoUnitRepository.save(attachmentVideoUnit);
    }

    /**
     * Creates and saves an AttachmentVideoUnit with an Attachment that has a file. Also creates and saves the given number of Slides for the AttachmentVideoUnit.
     * The Slides link to image files.
     *
     * @param lecture        The lecture the unit belongs to. If {@code null}, a placeholder lecture is persisted.
     * @param numberOfSlides The number of Slides to create
     * @param shouldBePdf    if true file will be pdf, else image
     * @return The created AttachmentVideoUnit
     */
    public AttachmentVideoUnit createAttachmentVideoUnitWithSlidesAndFile(Lecture lecture, int numberOfSlides, boolean shouldBePdf) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        AttachmentVideoUnit attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setDescription("Lorem Ipsum");
        attachmentVideoUnit.setLecture(lecture);
        attachmentVideoUnit = attachmentVideoUnitRepository.save(attachmentVideoUnit);
        Attachment attachmentOfAttachmentVideoUnit = shouldBePdf ? LectureFactory.generateAttachmentWithPdfFile(started, attachmentVideoUnit.getId(), true)
                : LectureFactory.generateAttachmentWithFile(started, attachmentVideoUnit.getId(), true);
        attachmentOfAttachmentVideoUnit.setAttachmentVideoUnit(attachmentVideoUnit);
        attachmentOfAttachmentVideoUnit = attachmentRepository.save(attachmentOfAttachmentVideoUnit);
        attachmentVideoUnit.setAttachment(attachmentOfAttachmentVideoUnit);
        attachmentVideoUnit = attachmentVideoUnitRepository.save(attachmentVideoUnit);
        for (int i = 1; i <= numberOfSlides; i++) {
            Slide slide = new Slide();
            slide.setSlideNumber(i);
            String testFileName = "slide" + i + ".png";

            slide.setAttachmentVideoUnit(attachmentVideoUnit);
            // we have to set a dummy value here, as null is not allowed. The correct value is set below.
            slide.setSlideImagePath("dummy");
            slide = slideRepository.save(slide);
            Path slidePath = FilePathConverter.getAttachmentVideoUnitFileSystemPath()
                    .resolve(Path.of(attachmentVideoUnit.getId().toString(), "slide", slide.getId().toString(), testFileName));
            try {
                FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/attachment/placeholder.jpg"), slidePath.toFile());
            }
            catch (IOException ex) {
                fail("Failed while copying test attachment files", ex);
            }
            // in the database we omit the prefix "uploads"
            var indexOfTheFirstSeperator = slidePath.toString().indexOf(FileSystems.getDefault().getSeparator());
            var slidePathWithoutFileUploadPathPrefix = slidePath.toString().substring(indexOfTheFirstSeperator + 1);
            slide.setSlideImagePath(slidePathWithoutFileUploadPathPrefix);
            slideRepository.save(slide);
        }
        return attachmentVideoUnitRepository.save(attachmentVideoUnit);
    }

    /**
     * Creates and saves an AttachmentVideoUnit with an Attachment. Also creates and saves the given number of Slides for the AttachmentVideoUnit. The Slides link to image files.
     *
     * @param numberOfSlides The number of Slides to create
     * @return The created AttachmentVideoUnit
     */
    public AttachmentVideoUnit createAttachmentVideoUnitWithSlides(Lecture lecture, int numberOfSlides) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        Attachment attachmentOfAttachmentVideoUnit = LectureFactory.generateAttachment(started);
        AttachmentVideoUnit attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setDescription("Lorem Ipsum");
        attachmentVideoUnit.setLecture(lecture);
        attachmentVideoUnit = attachmentVideoUnitRepository.save(attachmentVideoUnit);
        attachmentOfAttachmentVideoUnit.setAttachmentVideoUnit(attachmentVideoUnit);
        attachmentOfAttachmentVideoUnit = attachmentRepository.save(attachmentOfAttachmentVideoUnit);
        attachmentVideoUnit.setAttachment(attachmentOfAttachmentVideoUnit);
        for (int i = 1; i <= numberOfSlides; i++) {
            Slide slide = new Slide();
            slide.setSlideNumber(i);
            String testFileName = "slide" + i + ".png";
            try {
                FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/attachment/placeholder.jpg"), FilePathConverter.getTempFilePath().resolve(testFileName).toFile());
            }
            catch (IOException ex) {
                fail("Failed while copying test attachment files", ex);
            }
            slide.setSlideImagePath("temp/" + testFileName);
            slide.setAttachmentVideoUnit(attachmentVideoUnit);
            slideRepository.save(slide);
        }
        return attachmentVideoUnitRepository.save(attachmentVideoUnit);
    }

    /**
     * Creates and saves a TextUnit.
     *
     * @return The created TextUnit
     */
    public TextUnit createTextUnit(Lecture lecture) {
        TextUnit textUnit = new TextUnit();
        textUnit.setLecture(lecture);
        textUnit.setName("Name Lorem Ipsum");
        textUnit.setContent("Lorem Ipsum");
        return textUnitRepository.save(textUnit);
    }

    /**
     * Creates and saves an OnlineUnit.
     *
     * @return The created OnlineUnit
     */
    public OnlineUnit createOnlineUnit(Lecture lecture) {
        OnlineUnit onlineUnit = new OnlineUnit();
        onlineUnit.setLecture(lecture);
        onlineUnit.setDescription("Lorem Ipsum");
        onlineUnit.setSource("http://video.fake");
        return onlineUnitRepository.save(onlineUnit);
    }

    /**
     * Creates and saves a LectureUnitCompletion entry for the given LectureUnit and User.
     *
     * @param lectureUnit The LectureUnit that has been completed
     * @param user        The User that completed the LectureUnit
     * @return The completed LectureUnit
     */
    public LectureUnit completeLectureUnitForUser(LectureUnit lectureUnit, User user) {
        var lectureUnitCompletion = new LectureUnitCompletion();
        lectureUnitCompletion.setLectureUnit(lectureUnit);
        lectureUnitCompletion.setUser(user);
        lectureUnitCompletion.setCompletedAt(ZonedDateTime.now());
        lectureUnitCompletion = lectureUnitCompletionRepository.save(lectureUnitCompletion);

        if (Hibernate.isInitialized(lectureUnit.getCompletedUsers())) {
            lectureUnit.getCompletedUsers().add(lectureUnitCompletion);
        }

        return lectureUnitCompletion.getLectureUnit();
    }
}
