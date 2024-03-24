package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.lecture.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.post.ConversationFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.service.FilePathService;

/**
 * Service responsible for initializing the database with specific testdata related to lectures for use in integration tests.
 */
@Service
public class LectureUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private SlideRepository slideRepository;

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private VideoUnitRepository videoUnitRepository;

    @Autowired
    private OnlineUnitRepository onlineUnitRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ConversationRepository conversationRepository;

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
     * Creates and saves two Courses with Exercises of each type and two Lectures. For each Lecture, a LectureUnit of each type is added.
     *
     * @param userPrefix                  The prefix of the Course's user groups
     * @param withParticipations          True, if 5 participations by student1 should be added for the Course's Exercises
     * @param withFiles                   True, if the LectureUnit of type AttachmentUnit should contain an Attachment with a link to an image file
     * @param numberOfTutorParticipations The number of tutor participations to add to the ModelingExercise ("withParticipations" must be true for this to have an effect)
     * @return A List of the created Courses
     * @throws IOException If a file cannot be loaded from resources
     */
    public List<Course> createCoursesWithExercisesAndLecturesAndLectureUnits(String userPrefix, boolean withParticipations, boolean withFiles, int numberOfTutorParticipations)
            throws IOException {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(userPrefix, withParticipations, withFiles, numberOfTutorParticipations);
        return courses.stream().peek(course -> {
            List<Lecture> lectures = new ArrayList<>(course.getLectures());
            for (int i = 0; i < lectures.size(); i++) {
                TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst().orElseThrow();
                VideoUnit videoUnit = createVideoUnit();
                TextUnit textUnit = createTextUnit();
                AttachmentUnit attachmentUnit = createAttachmentUnit(withFiles);
                ExerciseUnit exerciseUnit = createExerciseUnit(textExercise);
                lectures.set(i, addLectureUnitsToLecture(lectures.get(i), List.of(videoUnit, textUnit, attachmentUnit, exerciseUnit)));
            }
            course.setLectures(new HashSet<>(lectures));
        }).toList();
    }

    /**
     * Adds the given Competencies to all LectureUnits of the given Lecture and saves the updated LectureUnits.
     *
     * @param lecture      The Lecture whose LectureUnits should be updated
     * @param competencies The Competencies to add to the LectureUnits
     * @return The Lecture with updated LectureUnits
     */
    public Lecture addCompetencyToLectureUnits(Lecture lecture, Set<Competency> competencies) {
        Lecture l = lectureRepo.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture.getId());
        l.getLectureUnits().forEach(lectureUnit -> {
            lectureUnit.setCompetencies(competencies);
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
        Lecture l = lectureRepo.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        for (LectureUnit lectureUnit : lectureUnits) {
            l.addLectureUnit(lectureUnit);
        }
        return lectureRepo.save(l);
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
    public ExerciseUnit createExerciseUnit(Exercise exercise) {
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        return exerciseUnitRepository.save(exerciseUnit);
    }

    /**
     * Creates and saves an AttachmentUnit with an Attachment. The Attachment can be created with or without a link to an image file.
     *
     * @param withFile True, if the Attachment should link to a file
     * @return The created AttachmentUnit
     */
    public AttachmentUnit createAttachmentUnit(Boolean withFile) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        Attachment attachmentOfAttachmentUnit = withFile ? LectureFactory.generateAttachmentWithFile(started, attachmentUnit.getId(), true)
                : LectureFactory.generateAttachment(started);
        attachmentOfAttachmentUnit.setAttachmentUnit(attachmentUnit);
        attachmentOfAttachmentUnit = attachmentRepository.save(attachmentOfAttachmentUnit);
        attachmentUnit.setAttachment(attachmentOfAttachmentUnit);
        return attachmentUnitRepository.save(attachmentUnit);
    }

    /**
     * Creates and saves an AttachmentUnit with an Attachment. Also creates and saves the given number of Slides for the AttachmentUnit. The Slides link to image files.
     *
     * @param numberOfSlides The number of Slides to create
     * @return The created AttachmentUnit
     */
    public AttachmentUnit createAttachmentUnitWithSlides(int numberOfSlides) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        Attachment attachmentOfAttachmentUnit = LectureFactory.generateAttachment(started);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        attachmentOfAttachmentUnit.setAttachmentUnit(attachmentUnit);
        attachmentOfAttachmentUnit = attachmentRepository.save(attachmentOfAttachmentUnit);
        attachmentUnit.setAttachment(attachmentOfAttachmentUnit);
        for (int i = 1; i <= numberOfSlides; i++) {
            Slide slide = new Slide();
            slide.setSlideNumber(i);
            String testFileName = "slide" + i + ".png";
            try {
                FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/attachment/placeholder.jpg"), FilePathService.getTempFilePath().resolve(testFileName).toFile());
            }
            catch (IOException ex) {
                fail("Failed while copying test attachment files", ex);
            }
            slide.setSlideImagePath("/api/files/temp/" + testFileName);
            slide.setAttachmentUnit(attachmentUnit);
            slideRepository.save(slide);
        }
        return attachmentUnitRepository.save(attachmentUnit);
    }

    /**
     * Creates and saves a TextUnit.
     *
     * @return The created TextUnit
     */
    public TextUnit createTextUnit() {
        TextUnit textUnit = new TextUnit();
        textUnit.setName("Name Lorem Ipsum");
        textUnit.setContent("Lorem Ipsum");
        return textUnitRepository.save(textUnit);
    }

    /**
     * Creates and saves a VideoUnit.
     *
     * @return The created VideoUnit
     */
    public VideoUnit createVideoUnit() {
        VideoUnit videoUnit = new VideoUnit();
        videoUnit.setDescription("Lorem Ipsum");
        videoUnit.setSource("http://video.fake");
        return videoUnitRepository.save(videoUnit);
    }

    /**
     * Creates and saves an OnlineUnit.
     *
     * @return The created OnlineUnit
     */
    public OnlineUnit createOnlineUnit() {
        OnlineUnit onlineUnit = new OnlineUnit();
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
        return lectureUnitCompletion.getLectureUnit();
    }
}
