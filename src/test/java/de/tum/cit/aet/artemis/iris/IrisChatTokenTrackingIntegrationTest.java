/*
 * package de.tum.cit.aet.artemis.iris;
 * class IrisChatTokenTrackingIntegrationTest extends AbstractIrisIntegrationTest {
 * private static final String TEST_PREFIX = "irischattokentrackingintegration";
 * @Autowired
 * private IrisExerciseChatSessionService irisExerciseChatSessionService;
 * @Autowired
 * private IrisMessageRepository irisMessageRepository;
 * @Autowired
 * private LLMTokenUsageService llmTokenUsageService;
 * @Autowired
 * private LLMTokenUsageRepository irisLLMTokenUsageRepository;
 * @Autowired
 * private IrisRequestMockProvider irisRequestMockProvider;
 * @Autowired
 * private ParticipationUtilService participationUtilService;
 * @Autowired
 * private PyrisJobService pyrisJobService;
 * private ProgrammingExercise exercise;
 * private Course course;
 * private AtomicBoolean pipelineDone;
 * @BeforeEach
 * void initTestCase() throws GitAPIException, IOException, URISyntaxException {
 * userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
 * course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
 * exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
 * String projectKey = exercise.getProjectKey();
 * exercise.setProjectType(ProjectType.PLAIN_GRADLE);
 * exercise.setTestRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
 * programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
 * programmingExerciseRepository.save(exercise);
 * exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();
 * // Set the correct repository URIs for the template and the solution participation.
 * String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
 * TemplateProgrammingExerciseParticipation templateParticipation = exercise.getTemplateParticipation();
 * templateParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
 * templateProgrammingExerciseParticipationRepository.save(templateParticipation);
 * String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
 * SolutionProgrammingExerciseParticipation solutionParticipation = exercise.getSolutionParticipation();
 * solutionParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
 * solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
 * String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";
 * // Add a participation for student1.
 * ProgrammingExerciseStudentParticipation studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
 * studentParticipation.setRepositoryUri(String.format(localVCBaseUrl + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
 * studentParticipation.setBranch(defaultBranch);
 * programmingExerciseStudentParticipationRepository.save(studentParticipation);
 * // Prepare the repositories.
 * localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
 * localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
 * localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);
 * localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, assignmentRepositorySlug);
 * // Check that the repository folders were created in the file system for all base repositories.
 * localVCLocalCITestService.verifyRepositoryFoldersExist(exercise, localVCBasePath);
 * activateIrisGlobally();
 * activateIrisFor(course);
 * activateIrisFor(exercise);
 * // Clean up the database
 * irisLLMTokenUsageRepository.deleteAll();
 * pipelineDone = new AtomicBoolean(false);
 * }
 * @Test
 * @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
 * void testTokenTrackingHandledExerciseChat() throws Exception {
 * var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
 * var messageToSend = createDefaultMockMessage(irisSession);
 * var tokens = getMockLLMCosts();
 * List<PyrisStageDTO> doneStage = new ArrayList<>();
 * doneStage.add(new PyrisStageDTO("DoneTest", 10, PyrisStageState.DONE, "Done"));
 * irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
 * assertThat(dto.settings().authenticationToken()).isNotNull();
 * assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", doneStage, null, tokens));
 * pipelineDone.set(true);
 * });
 * request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);
 * await().until(pipelineDone::get);
 * List<LLMTokenUsage> savedTokenUsages = irisLLMTokenUsageRepository.findAll();
 * assertThat(savedTokenUsages).hasSize(5);
 * for (int i = 0; i < savedTokenUsages.size(); i++) {
 * LLMTokenUsage usage = savedTokenUsages.get(i);
 * PyrisLLMCostDTO expectedCost = tokens.get(i);
 * assertThat(usage.getModel()).isEqualTo(expectedCost.modelInfo());
 * assertThat(usage.getNumInputTokens()).isEqualTo(expectedCost.numInputTokens());
 * assertThat(usage.getNumOutputTokens()).isEqualTo(expectedCost.numOutputTokens());
 * assertThat(usage.getCostPerMillionInputTokens()).isEqualTo(expectedCost.costPerInputToken());
 * assertThat(usage.getCostPerMillionOutputTokens()).isEqualTo(expectedCost.costPerOutputToken());
 * assertThat(usage.getServiceType()).isEqualTo(expectedCost.pipeline());
 * }
 * }
 * @Test
 * @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
 * void testTokenTrackingSavedExerciseChat() {
 * var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
 * var irisMessage = createDefaultMockMessage(irisSession);
 * irisMessageRepository.save(irisMessage);
 * String jobToken = pyrisJobService.addExerciseChatJob(course.getId(), exercise.getId(), irisSession.getId());
 * PyrisJob job = pyrisJobService.getJob(jobToken);
 * var tokens = getMockLLMCosts();
 * // Capture the saved token usages
 * List<LLMTokenUsageTrace> returnedTokenUsages = llmTokenUsageService.saveLLMTokenUsage(
 * builder -> builder.withMessage(irisMessage).withExercise(exercise).withUser(irisSession.getUser()).withCourse(course).withTokens(tokens));
 * assertThat(returnedTokenUsages).hasSize(5);
 * for (int i = 0; i < returnedTokenUsages.size(); i++) {
 * LLMTokenUsage usage = returnedTokenUsages.get(i);
 * PyrisLLMCostDTO expectedCost = tokens.get(i);
 * assertThat(usage.getModel()).isEqualTo(expectedCost.modelInfo());
 * assertThat(usage.getNumInputTokens()).isEqualTo(expectedCost.numInputTokens());
 * assertThat(usage.getNumOutputTokens()).isEqualTo(expectedCost.numOutputTokens());
 * assertThat(usage.getCostPerMillionInputTokens()).isEqualTo(expectedCost.costPerInputToken());
 * assertThat(usage.getCostPerMillionOutputTokens()).isEqualTo(expectedCost.costPerOutputToken());
 * assertThat(usage.getServiceType()).isEqualTo(expectedCost.pipeline());
 * }
 * }
 * @Test
 * @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
 * void testTokenTrackingExerciseChatWithPipelineFail() throws Exception {
 * var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
 * var messageToSend = createDefaultMockMessage(irisSession);
 * var tokens = getMockLLMCosts();
 * List<PyrisStageDTO> failedStages = new ArrayList<>();
 * failedStages.add(new PyrisStageDTO("TestTokenFail", 10, PyrisStageState.ERROR, "Failed running pipeline"));
 * irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
 * assertThat(dto.settings().authenticationToken()).isNotNull();
 * assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), null, failedStages, null, tokens));
 * pipelineDone.set(true);
 * });
 * request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);
 * await().until(pipelineDone::get);
 * List<LLMTokenUsage> savedTokenUsages = irisLLMTokenUsageRepository.findAll();
 * assertThat(savedTokenUsages).hasSize(5);
 * for (int i = 0; i < savedTokenUsages.size(); i++) {
 * LLMTokenUsage usage = savedTokenUsages.get(i);
 * PyrisLLMCostDTO expectedCost = tokens.get(i);
 * assertThat(usage.getModel()).isEqualTo(expectedCost.modelInfo());
 * assertThat(usage.getNumInputTokens()).isEqualTo(expectedCost.numInputTokens());
 * assertThat(usage.getNumOutputTokens()).isEqualTo(expectedCost.numOutputTokens());
 * assertThat(usage.getCostPerMillionInputTokens()).isEqualTo(expectedCost.costPerInputToken());
 * assertThat(usage.getCostPerMillionOutputTokens()).isEqualTo(expectedCost.costPerOutputToken());
 * assertThat(usage.getServiceType()).isEqualTo(expectedCost.pipeline());
 * }
 * }
 * private List<LLMRequest> getMockLLMCosts() {
 * List<LLMRequest> costs = new ArrayList<>();
 * for (int i = 0; i < 5; i++) {
 * costs.add(new LLMRequest("test-llm", i * 10 + 5, i * 0.5f, i * 3 + 5, i * 0.12f, "IRIS_CHAT_EXERCISE_MESSAGE"));
 * }
 * return costs;
 * }
 * private IrisMessage createDefaultMockMessage(IrisSession irisSession) {
 * var messageToSend = irisSession.newMessage();
 * messageToSend.addContent(createMockTextContent(), createMockTextContent(), createMockTextContent());
 * return messageToSend;
 * }
 * private IrisMessageContent createMockTextContent() {
 * var text = "The happy dog jumped over the lazy dog.";
 * return new IrisTextMessageContent(text);
 * }
 * private void sendStatus(String jobId, String result, List<PyrisStageDTO> stages, List<String> suggestions, List<PyrisLLMCostDTO> tokens) throws Exception {
 * var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobId))));
 * request.postWithoutResponseBody("/api/public/pyris/pipelines/tutor-chat/runs/" + jobId + "/status", new PyrisChatStatusUpdateDTO(result, stages, suggestions, tokens),
 * HttpStatus.OK, headers);
 * }
 * }
 */
