package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.config.Constants.TEST_CASE_CHANGED_API_PATH;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javassist.NotFoundException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationUpdateService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.util.structureoraclegenerator.OracleGeneratorClient;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final FileService fileService;

    private final GitService gitService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService;

    private final SubmissionRepository submissionRepository;

    private final ParticipationRepository participationRepository;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ResourceLoader resourceLoader;

    @Value("${server.url}")
    private String ARTEMIS_BASE_URL;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService,
            Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService, ResourceLoader resourceLoader, SubmissionRepository submissionRepository,
            ParticipationRepository participationRepository, UserService userService, AuthorizationCheckService authCheckService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
        this.resourceLoader = resourceLoader;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
    }

    /**
     * Notifies all particpations of the given programmingExercise about changes of the test cases.
     *
     * @param programmingExercise The programmingExercise where the test cases got changed
     */
    public void notifyChangedTestCases(ProgrammingExercise programmingExercise, Object requestBody) {
        for (Participation participation : programmingExercise.getParticipations()) {

            ProgrammingSubmission submission = new ProgrammingSubmission();
            submission.setType(SubmissionType.TEST);
            submission.setSubmissionDate(ZonedDateTime.now());
            submission.setSubmitted(true);
            submission.setParticipation(participation);
            try {
                String lastCommitHash = versionControlService.get().getLastCommitHash(requestBody);
                log.info("create new programmingSubmission with commitHash: " + lastCommitHash);
                submission.setCommitHash(lastCommitHash);
            }
            catch (Exception ex) {
                log.error("Commit hash could not be parsed for submission from participation " + participation, ex);
            }

            submissionRepository.save(submission);
            participationRepository.save(participation);

            continuousIntegrationUpdateService.get().triggerUpdate(participation.getBuildPlanId(), false);
        }
    }

    public void addStudentIdToProjectName(Repository repo, ProgrammingExercise programmingExercise, Participation participation) {
        String studentId = participation.getStudent().getLogin();

        // Get all files in repository expect .git files
        List<String> allRepoFiles = listAllFilesInPath(repo.getLocalPath());

        // is Java programming language
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            // Filter all Eclipse .project files
            List<String> eclipseProjectFiles = allRepoFiles.stream().filter(s -> s.endsWith(".project")).collect(Collectors.toList());

            for (String eclipseProjectFilePath : eclipseProjectFiles) {
                File eclipseProjectFile = new File(eclipseProjectFilePath);
                // Check if file exists and full file name is .project and not just the file ending.
                if (!eclipseProjectFile.exists() || !eclipseProjectFile.getName().equals(".project")) {
                    continue;
                }

                try {
                    // 1- Build the doc from the XML file
                    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(eclipseProjectFile.getPath()));
                    doc.setXmlStandalone(true);

                    // 2- Find the node with xpath
                    XPath xPath = XPathFactory.newInstance().newXPath();
                    Node nameNode = (Node) xPath.compile("/projectDescription/name").evaluate(doc, XPathConstants.NODE);

                    // 3- Append Student Id to Project Name
                    if (nameNode != null) {
                        nameNode.setTextContent(nameNode.getTextContent() + " " + studentId);
                    }

                    // 4- Save the result to a new XML doc
                    Transformer xformer = TransformerFactory.newInstance().newTransformer();
                    xformer.transform(new DOMSource(doc), new StreamResult(new File(eclipseProjectFile.getPath())));

                }
                catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
                    log.error("Cannot rename .project file in " + repo.getLocalPath() + " due to the following exception: " + ex);
                }
            }

            // Filter all pom.xml files
            List<String> pomFiles = allRepoFiles.stream().filter(s -> s.endsWith("pom.xml")).collect(Collectors.toList());
            for (String pomFilePath : pomFiles) {
                File pomFile = new File(pomFilePath);
                // check if file exists and full file name is pom.xml and not just the file ending.
                if (!pomFile.exists() || !pomFile.getName().equals("pom.xml")) {
                    continue;
                }

                try {
                    // 1- Build the doc from the XML file
                    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(pomFile.getPath()));
                    doc.setXmlStandalone(true);

                    // 2- Find the relevant nodes with xpath
                    XPath xPath = XPathFactory.newInstance().newXPath();
                    Node nameNode = (Node) xPath.compile("/project/name").evaluate(doc, XPathConstants.NODE);
                    Node artifactIdNode = (Node) xPath.compile("/project/artifactId").evaluate(doc, XPathConstants.NODE);

                    // 3- Append Student Id to Project Names
                    if (nameNode != null) {
                        nameNode.setTextContent(nameNode.getTextContent() + " " + studentId);
                    }
                    if (artifactIdNode != null) {
                        String artifactId = (artifactIdNode.getTextContent() + "-" + studentId).replaceAll(" ", "-").toLowerCase();
                        artifactIdNode.setTextContent(artifactId);
                    }

                    // 4- Save the result to a new XML doc
                    Transformer xformer = TransformerFactory.newInstance().newTransformer();
                    xformer.transform(new DOMSource(doc), new StreamResult(new File(pomFile.getPath())));

                }
                catch (SAXException | IOException | ParserConfigurationException | TransformerException | XPathException ex) {
                    log.error("Cannot rename pom.xml file in " + repo.getLocalPath() + " due to the following exception: " + ex);
                }
            }
        }

        try {
            gitService.stageAllChanges(repo);
            gitService.commit(repo, "Add Student Id to Project Name");
        }
        catch (GitAPIException ex) {
            log.error("Cannot stage or commit to the repo " + repo.getLocalPath() + " due to the following exception: " + ex);
        }
    }

    /**
     * Get all files in path expect .git files
     *
     * @param path
     */
    private List<String> listAllFilesInPath(Path path) {
        List<String> allRepoFiles = null;
        try (Stream<Path> walk = Files.walk(path)) {
            allRepoFiles = walk.filter(Files::isRegularFile).map(x -> x.toString()).filter(s -> !s.contains(".git")).collect(Collectors.toList());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return allRepoFiles;
    }

    /**
     * Setups all needed repositories etc. for the given programmingExercise.
     *
     * @param programmingExercise The programmingExercise that should be setup
     */
    public ProgrammingExercise setupProgrammingExercise(ProgrammingExercise programmingExercise) throws Exception {
        String projectKey = programmingExercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-exercise";
        String testRepoName = projectKey.toLowerCase() + "-tests";
        String solutionRepoName = projectKey.toLowerCase() + "-solution";

        // Create VCS repositories
        versionControlService.get().createProjectForExercise(programmingExercise); // Create project
        versionControlService.get().createRepository(projectKey, exerciseRepoName, null); // Create template repository
        versionControlService.get().createRepository(projectKey, testRepoName, null); // Create tests repository
        versionControlService.get().createRepository(projectKey, solutionRepoName, null); // Create solution repository

        Participation templateParticipation = programmingExercise.getTemplateParticipation();
        if (templateParticipation == null) {
            templateParticipation = new Participation();
            programmingExercise.setTemplateParticipation(templateParticipation);
        }
        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        if (solutionParticipation == null) {
            solutionParticipation = new Participation();
            programmingExercise.setSolutionParticipation(solutionParticipation);
        }

        initParticipations(programmingExercise);

        templateParticipation.setBuildPlanId(projectKey + "-BASE"); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneURL(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(projectKey + "-SOLUTION");
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneURL(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.get().getCloneURL(projectKey, testRepoName).toString());

        // Save participations to get the ids required for the webhooks
        templateParticipation.setExercise(programmingExercise);
        solutionParticipation.setExercise(programmingExercise);
        templateParticipation = participationRepository.save(templateParticipation);
        solutionParticipation = participationRepository.save(solutionParticipation);

        URL exerciseRepoUrl = versionControlService.get().getCloneURL(projectKey, exerciseRepoName);
        URL testsRepoUrl = versionControlService.get().getCloneURL(projectKey, testRepoName);
        URL solutionRepoUrl = versionControlService.get().getCloneURL(projectKey, solutionRepoName);

        String programmingLanguage = programmingExercise.getProgrammingLanguage().toString().toLowerCase();

        String templatePath = "classpath:templates/" + programmingLanguage;
        String exercisePath = templatePath + "/exercise/**/*.*";
        String solutionPath = templatePath + "/solution/**/*.*";
        String testPath = templatePath + "/test/**/*.*";

        Resource[] exerciseResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(exercisePath);
        Resource[] testResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(testPath);
        Resource[] solutionResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(solutionPath);

        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl);
        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl);
        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl);

        try {
            String exercisePrefix = programmingLanguage + File.separator + "exercise";
            String testPrefix = programmingLanguage + File.separator + "test";
            String solutionPrefix = programmingLanguage + File.separator + "solution";
            setupTemplateAndPush(exerciseRepo, exerciseResources, exercisePrefix, "Exercise", programmingExercise);
            setupTemplateAndPush(solutionRepo, solutionResources, solutionPrefix, "Solution", programmingExercise);
            setupTestTemplateAndPush(testRepo, testResources, testPrefix, "Test", programmingExercise);

        }
        catch (Exception ex) {
            // if any exception occurs, try to at least push an empty commit, so that the
            // repositories can
            // be used by the build plans
            log.warn("An exception occurred while setting up the repositories", ex);
            gitService.commitAndPush(exerciseRepo, "Empty Setup by Artemis");
            gitService.commitAndPush(testRepo, "Empty Setup by Artemis");
            gitService.commitAndPush(solutionRepo, "Empty Setup by Artemis");
        }

        // The creation of the webhooks must occur after the initial push, because the
        // participation is
        // not yet saved in the database, so we cannot save the submission accordingly
        // (see
        // ProgrammingSubmissionService.notifyPush)
        versionControlService.get().addWebHook(templateParticipation.getRepositoryUrlAsUrl(),
                ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + templateParticipation.getId(), "ArTEMiS WebHook");
        versionControlService.get().addWebHook(solutionParticipation.getRepositoryUrlAsUrl(),
                ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + solutionParticipation.getId(), "ArTEMiS WebHook");

        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, RepositoryType.TEMPLATE.getName(), exerciseRepoName, testRepoName); // template build
                                                                                                                                                               // plan
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, RepositoryType.SOLUTION.getName(), solutionRepoName, testRepoName); // solution build
                                                                                                                                                               // plan

        // save to get the id required for the webhook
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        versionControlService.get().addWebHook(testsRepoUrl, ARTEMIS_BASE_URL + TEST_CASE_CHANGED_API_PATH + programmingExercise.getId(), "ArTEMiS Tests WebHook");

        return programmingExercise;
    }

    // Copy template and push, if no file is in the directory
    private void setupTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise) throws Exception {
        if (gitService.listFiles(repository).size() == 0) { // Only copy template if repo is empty
            fileService.copyResources(resources, prefix, repository.getLocalPath().toAbsolutePath().toString(), true);
            replacePlaceholders(programmingExercise, repository);
            pushRepository(repository, templateName);
        }
    }

    /**
     * Set up the test repository. This method differentiates non sequential and sequential test repositories (more than 1 test job).
     *
     * @param repository
     * @param resources
     * @param prefix
     * @param templateName
     * @param programmingExercise
     * @throws Exception
     */
    private void setupTestTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise)
            throws Exception {
        if (gitService.listFiles(repository).size() == 0 && programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) { // Only copy template if repo is empty
            String templatePath = "classpath:templates/" + programmingExercise.getProgrammingLanguage().toString().toLowerCase() + "/test";

            String projectTemplatePath = templatePath + "/projectTemplate/**/*.*";
            String testUtilsPath = templatePath + "/testutils/**/*.*";

            Resource[] testUtils = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(testUtilsPath);
            Resource[] projectTemplate = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(projectTemplatePath);

            Map<String, Boolean> sectionsMap = new HashMap<>();

            fileService.copyResources(projectTemplate, prefix, repository.getLocalPath().toAbsolutePath().toString(), false);

            if (!programmingExercise.getSequentialTestRuns()) {
                String testFilePath = templatePath + "/testFiles" + "/**/*.*";
                Resource[] testFileResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(testFilePath);

                sectionsMap.put("non-sequential", true);
                sectionsMap.put("sequential", false);

                fileService.replacePlaceholderSections(Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "pom.xml").toAbsolutePath().toString(), sectionsMap);

                String packagePath = Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "test", "${packageNameFolder}").toAbsolutePath().toString();
                fileService.copyResources(testUtils, prefix, packagePath, true);
                fileService.copyResources(testFileResources, prefix, packagePath, false);
            }
            else {
                String stagePomXmlPath = templatePath + "/stagePom.xml";
                Resource stagePomXml = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource(stagePomXmlPath);
                // This is done to prepare for a feature where instructors/tas can add multiple build stages.
                List<String> buildStages = new ArrayList<>();
                buildStages.add("structural");
                buildStages.add("behavior");

                sectionsMap.put("non-sequential", false);
                sectionsMap.put("sequential", true);

                fileService.replacePlaceholderSections(Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "pom.xml").toAbsolutePath().toString(), sectionsMap);

                for (String buildStage : buildStages) {

                    Path buildStagePath = Paths.get(repository.getLocalPath().toAbsolutePath().toString(), buildStage);
                    Files.createDirectory(buildStagePath);

                    String buildStageResourcesPath = templatePath + "/testFiles/" + buildStage + "/**/*.*";
                    Resource[] buildStageResources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(buildStageResourcesPath);

                    Files.createDirectory(Paths.get(buildStagePath.toAbsolutePath().toString(), "test"));
                    Files.createDirectory(Paths.get(buildStagePath.toAbsolutePath().toString(), "test", "${packageNameFolder}"));

                    String packagePath = Paths.get(buildStagePath.toAbsolutePath().toString(), "test", "${packageNameFolder}").toAbsolutePath().toString();

                    Files.copy(stagePomXml.getInputStream(), Paths.get(buildStagePath.toAbsolutePath().toString(), "pom.xml"));
                    fileService.copyResources(testUtils, prefix, packagePath, true);
                    fileService.copyResources(buildStageResources, prefix, packagePath, false);
                }
            }

            replacePlaceholders(programmingExercise, repository);
            pushRepository(repository, templateName);
        }
        else {
            // If there is no special test structure for a programming language, just copy all the test files.
            setupTemplateAndPush(repository, resources, prefix, templateName, programmingExercise);
        }
    }

    /**
     * Replace placeholders in repository files (e.g. ${placeholder}).
     * 
     * @param programmingExercise
     * @param repository
     * @throws IOException
     */
    public void replacePlaceholders(ProgrammingExercise programmingExercise, Repository repository) throws IOException {
        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            fileService.replaceVariablesInDirectoryName(repository.getLocalPath().toAbsolutePath().toString(), "${packageNameFolder}", programmingExercise.getPackageFolderName());
        }

        List<String> fileTargets = new ArrayList<>();
        List<String> fileReplacements = new ArrayList<>();
        // This is based on the correct order and assumes that boths lists have the same
        // length, it
        // replaces fileTargets.get(i) with fileReplacements.get(i)

        if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.JAVA) {
            fileTargets.add("${packageName}");
            fileReplacements.add(programmingExercise.getPackageName());
        }
        // there is no need in python to replace package names

        fileTargets.add("${exerciseNamePomXml}");
        fileReplacements.add(programmingExercise.getTitle().replaceAll(" ", "-")); // Used e.g. in artifactId

        fileTargets.add("${exerciseName}");
        fileReplacements.add(programmingExercise.getTitle());

        fileService.replaceVariablesInFileRecursive(repository.getLocalPath().toAbsolutePath().toString(), fileTargets, fileReplacements);
    }

    /**
     * Stage, commit and push.
     * 
     * @param repository
     * @param templateName
     * @throws GitAPIException
     */
    public void pushRepository(Repository repository, String templateName) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, templateName + "-Template pushed by Artemis");
        repository.setFiles(null); // Clear cache to avoid multiple commits when ArTEMiS server is not restarted between attempts
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the template Participation
     *
     * @param participation The template participation
     * @return The ProgrammingExercise where the given Participation is the template Participation
     */
    public ProgrammingExercise getExerciseForTemplateParticipation(Participation participation) {
        return programmingExerciseRepository.findOneByTemplateParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution Participation
     *
     * @param participation The solution participation
     * @return The ProgrammingExercise where the given Participation is the solution Participation
     */
    public ProgrammingExercise getExerciseForSolutionParticipation(Participation participation) {
        return programmingExerciseRepository.findOneBySolutionParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution or template Participation
     *
     * @param participation The solution or template participation
     * @return The ProgrammingExercise where the given Participation is the solution or template Participation
     */
    public Optional<ProgrammingExercise> getExerciseForSolutionOrTemplateParticipation(Participation participation) {
        return programmingExerciseRepository.findOneByTemplateParticipationIdOrSolutionParticipationId(participation.getId());
    }

    /**
     * This methods sets the values (initialization date and initialization state) of the template and solution participation
     *
     * @param programmingExercise The programming exercise
     */
    public void initParticipations(ProgrammingExercise programmingExercise) {

        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        Participation templateParticipation = programmingExercise.getTemplateParticipation();

        solutionParticipation.setInitializationState(InitializationState.INITIALIZED);
        templateParticipation.setInitializationState(InitializationState.INITIALIZED);
        solutionParticipation.setInitializationDate(ZonedDateTime.now());
        templateParticipation.setInitializationDate(ZonedDateTime.now());
    }

    /**
     * Find a programming exercise by its id.
     * 
     * @param id of the programming exercise.
     * @return
     * @throws NotFoundException      the programming exercise could not be found.
     * @throws IllegalAccessException the retriever does not have the permissions to fetch information related to the programming exercise.
     */
    public ProgrammingExercise findById(Long id) throws NotFoundException, IllegalAccessException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(id);
        if (programmingExercise.isPresent()) {
            Course course = programmingExercise.get().getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                throw new IllegalAccessException();
            }
            return programmingExercise.get();
        }
        else {
            throw new NotFoundException("programming exercise not found");
        }
    }

    /**
     * This method saves the participations of the programming xercise
     *
     * @param programmingExercise The programming exercise
     */
    public void saveParticipations(ProgrammingExercise programmingExercise) {
        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        Participation templateParticipation = programmingExercise.getTemplateParticipation();

        participationRepository.save(solutionParticipation);
        participationRepository.save(templateParticipation);
    }

    /**
     * Squash all commits of the given repository into one.
     * 
     * @param repoUrl of the repository to squash.
     * @throws IOException
     * @throws InterruptedException
     * @throws IllegalStateException
     */
    public void squashAllCommitsOfRepositoryIntoOne(URL repoUrl) throws IOException, InterruptedException, IllegalStateException, GitAPIException {
        Repository exerciseRepository = gitService.getOrCheckoutRepository(repoUrl);
        gitService.squashAllCommitsIntoInitialCommit(exerciseRepository);
    }

    /**
     * This method calls the StructureOracleGenerator, generates the string out of the JSON representation of the structure oracle of the programming exercise and returns true if
     * the file was updated or generated, false otherwise. This can happen if the contents of the file have not changed.
     *
     * @param solutionRepoURL The URL of the solution repository.
     * @param exerciseRepoURL The URL of the exercise repository.
     * @param testRepoURL     The URL of the tests repository.
     * @param testsPath       The path to the tests folder, e.g. the path inside the repository where the structure oracle file will be saved in.
     * @return True, if the structure oracle was successfully generated or updated, false if no changes to the file were made.
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean generateStructureOracleFile(URL solutionRepoURL, URL exerciseRepoURL, URL testRepoURL, String testsPath) throws IOException, InterruptedException {
        Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepoURL);
        Repository exerciseRepository = gitService.getOrCheckoutRepository(exerciseRepoURL);
        Repository testRepository = gitService.getOrCheckoutRepository(testRepoURL);

        gitService.resetToOriginMaster(solutionRepository);
        gitService.pull(solutionRepository);
        gitService.resetToOriginMaster(exerciseRepository);
        gitService.pull(exerciseRepository);
        gitService.resetToOriginMaster(testRepository);
        gitService.pull(testRepository);

        Path solutionRepositoryPath = solutionRepository.getLocalPath().toRealPath();
        Path exerciseRepositoryPath = exerciseRepository.getLocalPath().toRealPath();
        Path structureOraclePath = Paths.get(testRepository.getLocalPath().toRealPath().toString(), testsPath, "test.json");

        String structureOracleJSON = OracleGeneratorClient.generateStructureOracleJSON(solutionRepositoryPath, exerciseRepositoryPath);

        // If the oracle file does not already exist, then save the generated string to
        // the file.
        // If it does, check if the contents of the existing file are the same as the
        // generated one.
        // If they are, do not push anything and inform the user about it.
        // If not, then update the oracle file by rewriting it and push the changes.
        if (!Files.exists(structureOraclePath)) {
            try {
                Files.write(structureOraclePath, structureOracleJSON.getBytes());
                gitService.stageAllChanges(testRepository);
                gitService.commitAndPush(testRepository, "Generate the structure oracle file.");
                return true;
            }
            catch (GitAPIException e) {
                log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                return false;
            }
        }
        else {
            Byte[] existingContents = ArrayUtils.toObject(Files.readAllBytes(structureOraclePath));
            Byte[] newContents = ArrayUtils.toObject(structureOracleJSON.getBytes());

            if (Arrays.deepEquals(existingContents, newContents)) {
                log.info("No changes to the oracle detected.");
                return false;
            }
            else {
                try {
                    Files.write(structureOraclePath, structureOracleJSON.getBytes());
                    gitService.stageAllChanges(testRepository);
                    gitService.commitAndPush(testRepository, "Update the structure oracle file.");
                    return true;
                }
                catch (GitAPIException e) {
                    log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                    return false;
                }
            }
        }
    }
}
