package de.tum.in.www1.artemis;

public class ExamActivityIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {
    /*
     * /** Used to wrap the test calls for the added actions
     * @param studentExam student exam
     * @param input received action DTO from the client
     * @throws Exception exception private ExamAction synchronizeExamActionHelper(StudentExam studentExam, ExamActionDTO input) throws Exception { // Participate as student var
     * user = studentExam.getUser(); database.changeUser(user.getLogin()); List<ExamActionDTO> actions = List.of(input); // Make request to add actions request.put("/api/courses/"
     * + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/actions", actions, HttpStatus.OK); studentExam =
     * studentExamRepository.findById(studentExam.getId()).orElseThrow(); assertThat(studentExam.getExamActivity()).isNotNull(); // Receive the ExamActivity ExamActivity
     * examActivity = studentExam.getExamActivity(); assertThat(examActivity.getExamActions().size()).isEqualTo(1); // Expect that the list of ExamActions contains the added
     * ExamAction List<ExamAction> examActions = new ArrayList<>(examActivity.getExamActions()); return examActions.get(0); }
     * @Test
     * @WithMockUser(username = "instructor1", roles = "INSTRUCTOR") public void testSynchronizeStartExamAction() throws Exception { final var studentExams =
     * prepareStudentExamsForConduction(false); var studentExam = studentExams.get(0); // Participate as student to create initial exam session var user = studentExam.getUser();
     * database.changeUser(user.getLogin()); studentExam = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.OK,
     * StudentExam.class); ExamSession session = new ArrayList<>(studentExam.getExamSessions()).get(studentExam.getExamSessions().size() - 1); // Set timestamp of action
     * ZonedDateTime timestamp = ZonedDateTime.now(); // Create DTO StartedExamActionDTO examActionDTO = new StartedExamActionDTO();
     * examActionDTO.setExamSessionId(session.getId()); examActionDTO.setType(ExamActionType.STARTED_EXAM); examActionDTO.setTimestamp(timestamp); // Expected StartedExamAction
     * StartedExamAction result = (StartedExamAction) synchronizeExamActionHelper(studentExam, examActionDTO); assertThat(result.getType()).isEqualTo(ExamActionType.STARTED_EXAM);
     * assertThat(result.getExamSession()).isEqualTo(session); }
     * @Test
     * @WithMockUser(username = "instructor1", roles = "INSTRUCTOR") public void testSynchronizeEndedExamAction() throws Exception { final var studentExams =
     * prepareStudentExamsForConduction(false); var studentExam = studentExams.get(0); // Set timestamp of action ZonedDateTime timestamp = ZonedDateTime.now(); // Create DTO
     * ExamActionDTO examActionDTO = new ExamActionDTO(); examActionDTO.setType(ExamActionType.ENDED_EXAM); examActionDTO.setTimestamp(timestamp); // Expected EndedExamAction
     * EndedExamAction result = (EndedExamAction) synchronizeExamActionHelper(studentExam, examActionDTO); assertThat(result.getType()).isEqualTo(ExamActionType.ENDED_EXAM); }
     * @Test
     * @WithMockUser(username = "instructor1", roles = "INSTRUCTOR") public void testSynchronizeContinuedAfterHandedInEarlyAction() throws Exception { final var studentExams =
     * prepareStudentExamsForConduction(false); var studentExam = studentExams.get(0); // Set timestamp of action ZonedDateTime timestamp = ZonedDateTime.now(); // Create DTO
     * ExamActionDTO examActionDTO = new ExamActionDTO(); examActionDTO.setType(ExamActionType.CONTINUED_AFTER_HAND_IN_EARLY); examActionDTO.setTimestamp(timestamp); // Expected
     * ContinuedAfterHandedInEarlyAction ContinuedAfterHandedInEarlyAction result = (ContinuedAfterHandedInEarlyAction) synchronizeExamActionHelper(studentExam, examActionDTO);
     * assertThat(result.getType()).isEqualTo(ExamActionType.CONTINUED_AFTER_HAND_IN_EARLY); }
     * @Test
     * @WithMockUser(username = "instructor1", roles = "INSTRUCTOR") public void testSynchronizeConnectionUpdatedAction() throws Exception { final var studentExams =
     * prepareStudentExamsForConduction(false); var studentExam = studentExams.get(0); // Set timestamp of action ZonedDateTime timestamp = ZonedDateTime.now(); // Create DTO
     * ConnectionUpdatedActionDTO examActionDTO = new ConnectionUpdatedActionDTO(); examActionDTO.setType(ExamActionType.CONNECTION_UPDATED); examActionDTO.setTimestamp(timestamp);
     * examActionDTO.setConnected(false); // Expected ConnectionUpdatedAction ConnectionUpdatedAction result = (ConnectionUpdatedAction) synchronizeExamActionHelper(studentExam,
     * examActionDTO); assertThat(result.getType()).isEqualTo(ExamActionType.CONNECTION_UPDATED); assertThat(result.isConnected()).isEqualTo(false); }
     * @Test
     * @WithMockUser(username = "instructor1", roles = "INSTRUCTOR") public void testSynchronizeHandedInEarlyAction() throws Exception { final var studentExams =
     * prepareStudentExamsForConduction(false); var studentExam = studentExams.get(0); // Set timestamp of action ZonedDateTime timestamp = ZonedDateTime.now(); // Create DTO
     * ExamActionDTO examActionDTO = new ExamActionDTO(); examActionDTO.setType(ExamActionType.HANDED_IN_EARLY); examActionDTO.setTimestamp(timestamp); // Expected
     * HandedInEarlyAction HandedInEarlyAction result = (HandedInEarlyAction) synchronizeExamActionHelper(studentExam, examActionDTO);
     * assertThat(result.getType()).isEqualTo(ExamActionType.HANDED_IN_EARLY); }
     * @Test
     * @WithMockUser(username = "instructor1", roles = "INSTRUCTOR") public void testSynchronizeSavedExerciseAction() throws Exception { final var studentExams =
     * prepareStudentExamsForConduction(false); var studentExam = studentExams.get(0); // Set timestamp of action ZonedDateTime timestamp = ZonedDateTime.now(); // Create
     * submission for exercise Submission submission = submissionRepository.save(new FileUploadSubmission()); // Create DTO SavedExerciseActionDTO examActionDTO = new
     * SavedExerciseActionDTO(); examActionDTO.setType(ExamActionType.SAVED_EXERCISE); examActionDTO.setTimestamp(timestamp); examActionDTO.setAutomatically(false);
     * examActionDTO.setFailed(true); examActionDTO.setForced(true); examActionDTO.setSubmissionId(submission.getId()); // Expected SavedExerciseAction SavedExerciseAction result =
     * (SavedExerciseAction) synchronizeExamActionHelper(studentExam, examActionDTO); assertThat(result.getType()).isEqualTo(ExamActionType.SAVED_EXERCISE);
     * assertThat(result.isAutomatically()).isEqualTo(false); assertThat(result.isFailed()).isEqualTo(true); assertThat(result.isForced()).isEqualTo(true);
     * assertThat(result.getSubmission()).isEqualTo(submission); }
     * @Test
     * @WithMockUser(username = "instructor1", roles = "INSTRUCTOR") public void testSynchronizeSwitchedExerciseAction() throws Exception { final var studentExams =
     * prepareStudentExamsForConduction(false); var studentExam = studentExams.get(0); // Set timestamp of action ZonedDateTime timestamp = ZonedDateTime.now(); // Get exercise
     * Exercise exercise = exam2.getExerciseGroups().get(0).getExercises().stream().findFirst().orElseThrow(); // Create DTO SwitchedExerciseActionDTO examActionDTO = new
     * SwitchedExerciseActionDTO(); examActionDTO.setType(ExamActionType.SWITCHED_EXERCISE); examActionDTO.setTimestamp(timestamp); examActionDTO.setExerciseId(exercise.getId());
     * // Expected SwitchedExerciseAction SwitchedExerciseAction result = (SwitchedExerciseAction) synchronizeExamActionHelper(studentExam, examActionDTO);
     * assertThat(result.getType()).isEqualTo(ExamActionType.SWITCHED_EXERCISE); assertThat(result.getExercise()).isEqualTo(exercise); }
     */
}
