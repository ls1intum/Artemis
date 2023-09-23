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

    public Lecture createLecture(Course course, ZonedDateTime visibleDate) {
        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        lecture.setVisibleDate(visibleDate);
        lectureRepo.save(lecture);
        return lecture;
    }

    public List<Course> createCoursesWithExercisesAndLecturesAndLectureUnits(String userPrefix, boolean withParticipations, boolean withFiles, int numberOfTutorParticipations)
            throws Exception {
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

    public Lecture addCompetencyToLectureUnits(Lecture lecture, Set<Competency> competencies) {
        Lecture l = lectureRepo.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture.getId());
        l.getLectureUnits().forEach(lectureUnit -> {
            lectureUnit.setCompetencies(competencies);
            lectureUnitRepository.save(lectureUnit);
        });
        return l;
    }

    public Lecture addLectureUnitsToLecture(Lecture lecture, List<LectureUnit> lectureUnits) {
        Lecture l = lectureRepo.findByIdWithLectureUnits(lecture.getId()).orElseThrow();
        for (LectureUnit lectureUnit : lectureUnits) {
            l.addLectureUnit(lectureUnit);
        }
        return lectureRepo.save(l);
    }

    public Channel addLectureChannel(Lecture lecture) {
        Channel channel = ConversationFactory.generateCourseWideChannel(lecture.getCourse());
        channel.setLecture(lecture);
        return conversationRepository.save(channel);
    }

    public ExerciseUnit createExerciseUnit(Exercise exercise) {
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        return exerciseUnitRepository.save(exerciseUnit);
    }

    public AttachmentUnit createAttachmentUnit(Boolean withFile) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        Attachment attachmentOfAttachmentUnit = withFile ? LectureFactory.generateAttachmentWithFile(started) : LectureFactory.generateAttachment(started);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        attachmentOfAttachmentUnit.setAttachmentUnit(attachmentUnit);
        attachmentOfAttachmentUnit = attachmentRepository.save(attachmentOfAttachmentUnit);
        attachmentUnit.setAttachment(attachmentOfAttachmentUnit);
        return attachmentUnitRepository.save(attachmentUnit);
    }

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

    public TextUnit createTextUnit() {
        TextUnit textUnit = new TextUnit();
        textUnit.setName("Name Lorem Ipsum");
        textUnit.setContent("Lorem Ipsum");
        return textUnitRepository.save(textUnit);
    }

    public VideoUnit createVideoUnit() {
        VideoUnit videoUnit = new VideoUnit();
        videoUnit.setDescription("Lorem Ipsum");
        videoUnit.setSource("http://video.fake");
        return videoUnitRepository.save(videoUnit);
    }

    public OnlineUnit createOnlineUnit() {
        OnlineUnit onlineUnit = new OnlineUnit();
        onlineUnit.setDescription("Lorem Ipsum");
        onlineUnit.setSource("http://video.fake");
        return onlineUnitRepository.save(onlineUnit);
    }
}
