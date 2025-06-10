package de.tum.cit.aet.artemis.iris;

class IrisChatSessionResourceIntegrationTest extends AbstractIrisIntegrationTest {

    // private static final String TEST_PREFIX = "irischatsessionresource";
    //
    // @Autowired
    // private CourseUtilService courseUtilService;
    //
    // @Autowired
    // private LectureUtilService lectureUtilService;
    //
    // @Autowired
    // private ExerciseService exerciseService;
    //
    // @Autowired
    // private IrisSettingsService irisSettingsService;
    //
    // @Autowired
    // private IrisCourseChatSessionRepository irisCourseChatSessionRepository;
    //
    // @Autowired
    // private IrisLectureChatSessionRepository irisLectureChatSessionRepository;
    //
    // @Autowired
    // private IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;
    //
    // @Autowired
    // private IrisTextExerciseChatSessionRepository irisTextExerciseChatSessionRepository;
    //
    // private Course course;
    //
    // private Lecture lecture;
    //
    // private Exercise programmingExercise;
    //
    // private Exercise textExercise;
    //
    // @BeforeEach
    // void setup() {
    // userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
    //
    // course = courseUtilService.createCourse();
    // lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());
    // programmingExercise = exerciseService(course, null);
    // textExercise = exerciseUtilService.createTextExercise(course, null);
    //
    // activateIrisGlobally();
    // activateIrisFor(course);
    //
    // var settings = irisSettingsService.getRawIrisSettingsFor(course);
    // settings.getIrisCourseChatSettings().setEnabled(true);
    // settings.getIrisLectureChatSettings().setEnabled(true);
    // settings.getIrisProgrammingExerciseChatSettings().setEnabled(true);
    // settings.getIrisTextExerciseChatSettings().setEnabled(true);
    // irisSettingsService.saveIrisSettings(settings);
    // }
    //
    // private ProgrammingExercise createProgrammingExercise() {
    // ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exam.getExerciseGroups().getFirst());
    // programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));
    // programmingExercise = exerciseRepository.save(programmingExercise);
    // programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
    // exerciseGroupRepository.save(exam.getExerciseGroups().getFirst());
    // return programmingExercise;
    // }
    //
    // @Test
    // @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    // void getAllSessionsForCourse_returnsAllSessionTypesIfEnabledAndAccepted() {
    // // Accept LLM usage for user
    // var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    // user.setHasAcceptedExternalLLMUsage(true);
    // userTestRepository.save(user);
    //
    // // create sessions for each type
    // var courseSession = irisCourseChatSessionRepository.save(new IrisChatSession(course, user));
    // var lectureSession = irisLectureChatSessionRepository.save(new IrisLectureChatSession(lecture, user));
    // var progSession = irisExerciseChatSessionRepository.save(new IrisChatSession(programmingExercise, user));
    // var textSession = irisTextExerciseChatSessionRepository.save(new IrisChatSession(textExercise, user));
    //
    // var sessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", OK, IrisChatSession.class);
    // assertThat(sessions).extracting("id").containsExactlyInAnyOrder(courseSession.getId(), lectureSession.getId(), progSession.getId(), textSession.getId());
    // }
    //
    // @Test
    // @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    // void getAllSessionsForCourse_returnsEmptyIfNotAcceptedLLM() {
    // // User has NOT accepted LLM usage
    // var user = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
    // user.setHasAcceptedExternalLLMUsage(false);
    // userTestRepository.save(user);
    //
    // // create sessions for each type
    // irisCourseChatSessionRepository.save(new IrisChatSession(course, user));
    // irisLectureChatSessionRepository.save(new IrisLectureChatSession(lecture, user));
    // irisExerciseChatSessionRepository.save(new IrisChatSession(programmingExercise, user));
    // irisTextExerciseChatSessionRepository.save(new IrisChatSession(textExercise, user));
    //
    // var sessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", OK, IrisChatSession.class);
    // assertThat(sessions).isEmpty();
    // }
    //
    // @Test
    // @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    // void getAllSessionsForCourse_withDisabledTypes_returnsOnlyEnabled() {
    // var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    // user.setHasAcceptedExternalLLMUsage(true);
    // userTestRepository.save(user);
    //
    // // Only enable COURSE_CHAT and LECTURE_CHAT
    // var settings = irisSettingsService.getRawIrisSettingsFor(course);
    // settings.getIrisCourseChatSettings().setEnabled(true);
    // settings.getIrisLectureChatSettings().setEnabled(true);
    // settings.getIrisProgrammingExerciseChatSettings().setEnabled(false);
    // settings.getIrisTextExerciseChatSettings().setEnabled(false);
    // irisSettingsService.saveIrisSettings(settings);
    //
    // var courseSession = irisCourseChatSessionRepository.save(new IrisChatSession(course, user));
    // var lectureSession = irisLectureChatSessionRepository.save(new IrisLectureChatSession(lecture, user));
    // irisExerciseChatSessionRepository.save(new IrisChatSession(programmingExercise, user));
    // irisTextExerciseChatSessionRepository.save(new IrisChatSession(textExercise, user));
    //
    // var sessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", OK, IrisChatSession.class);
    // assertThat(sessions).extracting("id").containsExactlyInAnyOrder(courseSession.getId(), lectureSession.getId());
    // }
    //
    // @Test
    // @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    // void getAllSessionsForCourse_noSessions_returnsEmptyList() {
    // var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    // user.setHasAcceptedExternalLLMUsage(true);
    // userTestRepository.save(user);
    //
    // var sessions = request.getList("/api/iris/chat-history/" + course.getId() + "/sessions", OK, IrisChatSession.class);
    // assertThat(sessions).isEmpty();
    // }
}
