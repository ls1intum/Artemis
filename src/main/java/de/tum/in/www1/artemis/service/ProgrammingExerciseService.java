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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationUpdateService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.util.structureoraclegenerator.OracleGeneratorClient;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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

    private final StudentParticipationRepository studentParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ParticipationService participationService;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ResourceLoader resourceLoader;

    @Value("${server.url}")
    private String ARTEMIS_BASE_URL;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService,
            Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService, ResourceLoader resourceLoader, SubmissionRepository submissionRepository,
            ParticipationService participationService, StudentParticipationRepository studentParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, UserService userService,
            AuthorizationCheckService authCheckService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
        this.resourceLoader = resourceLoader;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participationService = participationService;
        this.submissionRepository = submissionRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
    }

    /**
     * Notifies all participations of the given programmingExercise (including the template & solution participation!) about changes of the test cases.
     * This method creates submissions for the participations so that the result when it comes in can be mapped to them.
     *
     * @param exerciseId of programming exercise the test cases got changed.
     * @param requestBody The request body received from the VCS
     * @return A list of created {@link ProgrammingSubmission programming submissions} for the changed exercise
     * @throws EntityNotFoundException If the exercise with the given ID does not exist
     */
    public List<ProgrammingSubmission> notifyChangedTestCases(Long exerciseId, Object requestBody) throws EntityNotFoundException {
        Optional<ProgrammingExercise> exerciseOpt = programmingExerciseRepository.findById(exerciseId);
        if (!exerciseOpt.isPresent())
            throw new EntityNotFoundException("Programming exercise with id " + exerciseId + " not found.");
        ProgrammingExercise programmingExercise = exerciseOpt.get();
        // All student repository builds and the builds of the template & solution repository must be triggered now!
        Set<ProgrammingExerciseParticipation> participations = new HashSet<>();
        participations.add(programmingExercise.getSolutionParticipation());
        participations.add(programmingExercise.getTemplateParticipation());
        participations.addAll(programmingExercise.getParticipations().stream().map(p -> (ProgrammingExerciseParticipation) p).collect(Collectors.toSet()));

        List<ProgrammingSubmission> submissions = new ArrayList<>();

        for (ProgrammingExerciseParticipation participation : participations) {
            ProgrammingSubmission submission = new ProgrammingSubmission();
            submission.setType(SubmissionType.TEST);
            submission.setSubmissionDate(ZonedDateTime.now());
            submission.setSubmitted(true);
            submission.setParticipation((Participation) participation);
            try {
                String lastCommitHash = versionControlService.get().getLastCommitHash(requestBody);
                log.info("create new programmingSubmission with commitHash: " + lastCommitHash + " for participation " + participation.getId());
                submission.setCommitHash(lastCommitHash);
            }
            catch (Exception ex) {
                log.error("Commit hash could not be parsed for submission from participation " + participation, ex);
            }

            ProgrammingSubmission storedSubmission = submissionRepository.save(submission);
            submissions.add(storedSubmission);
            // TODO: I think it is a problem that the test repository just triggers the build in bamboo before the submission is created.
            // It could be that Artemis is not available and the results come in before the submission is ready.
            continuousIntegrationUpdateService.get().triggerUpdate(participation.getBuildPlanId(), false);
        }
        return submissions;
    }

    /**
     * Adds the student id of the given student participation to the project name in all .project (Eclipse)
     * and pom.xml (Maven) files found in the given repository.
     *
     * @param repo The repository for which the student id should get added
     * @param programmingExercise The checked out exercise in the repository
     * @param participation The student participation for the student id, which should be added.
     */
    public void addStudentIdToProjectName(Repository repo, ProgrammingExercise programmingExercise, StudentParticipation participation) {
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
     * Get all files in path except .git files
     *
     * @param path The path for which all file names should be listed
     * @return A list of all file names under the given path
     */
    private List<String> listAllFilesInPath(Path path) {
        List<String> allRepoFiles = null;
        try (Stream<Path> walk = Files.walk(path)) {
            allRepoFiles = walk.filter(Files::isRegularFile).map(Path::toString).filter(s -> !s.contains(".git")).collect(Collectors.toList());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return allRepoFiles;
    }

    // TODO We too many many generic throws Exception declarations.
    /**
     * Setups the context of a new programming exercise. This includes:
     * <ul>
     *     <li>The VCS project</li>
     *     <li>All repositories (test, exercise, solution)</li>
     *     <li>The template and solution participation</li>
     *     <li>VCS webhooks</li>
     *     <li>Bamboo build plans</li>
     * </ul>
     *
     * @param programmingExercise The programmingExercise that should be setup
     * @throws Exception If anything goes wrong
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

        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        if (templateParticipation == null) {
            templateParticipation = new TemplateProgrammingExerciseParticipation();
            programmingExercise.setTemplateParticipation(templateParticipation);
        }
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
        if (solutionParticipation == null) {
            solutionParticipation = new SolutionProgrammingExerciseParticipation();
            programmingExercise.setSolutionParticipation(solutionParticipation);
        }

        initParticipations(programmingExercise);

        templateParticipation.setBuildPlanId(projectKey + "-BASE"); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneURL(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(projectKey + "-SOLUTION");
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneURL(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.get().getCloneURL(projectKey, testRepoName).toString());

        // Save participations to get the ids required for the webhooks
        templateParticipation.setProgrammingExercise(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        templateParticipation = templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionParticipation = solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

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

        Repository exerciseRepo = gitService.getOrCheckoutRepository(exerciseRepoUrl, true);
        Repository testRepo = gitService.getOrCheckoutRepository(testsRepoUrl, true);
        Repository solutionRepo = gitService.getOrCheckoutRepository(solutionRepoUrl, true);

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
            // repositories can be used by the build plans
            log.warn("An exception occurred while setting up the repositories", ex);
            gitService.commitAndPush(exerciseRepo, "Empty Setup by Artemis");
            gitService.commitAndPush(testRepo, "Empty Setup by Artemis");
            gitService.commitAndPush(solutionRepo, "Empty Setup by Artemis");
        }

        // The creation of the webhooks must occur after the initial push, because the participation is
        // not yet saved in the database, so we cannot save the submission accordingly (see ProgrammingSubmissionService.notifyPush)
        versionControlService.get().addWebHook(templateParticipation.getRepositoryUrlAsUrl(),
                ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + templateParticipation.getId(), "Artemis WebHook");
        versionControlService.get().addWebHook(solutionParticipation.getRepositoryUrlAsUrl(),
                ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + solutionParticipation.getId(), "Artemis WebHook");

        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, RepositoryType.TEMPLATE.getName(), exerciseRepoName, testRepoName); // template build
                                                                                                                                                               // plan
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, RepositoryType.SOLUTION.getName(), solutionRepoName, testRepoName); // solution build
                                                                                                                                                               // plan

        // save to get the id required for the webhook
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        versionControlService.get().addWebHook(testsRepoUrl, ARTEMIS_BASE_URL + TEST_CASE_CHANGED_API_PATH + programmingExercise.getId(), "Artemis Tests WebHook");

        return programmingExercise;
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

    // Copy template and push, if no file is in the directory
    private void setupTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise) throws Exception {
        if (gitService.listFiles(repository).size() == 0) { // Only copy template if repo is empty
            fileService.copyResources(resources, prefix, repository.getLocalPath().toAbsolutePath().toString(), true);
            replacePlaceholders(programmingExercise, repository);
            commitAndPushRepository(repository, templateName);
        }
    }

    /**
     * Set up the test repository. This method differentiates non sequential and sequential test repositories (more than 1 test job).
     *
     * @param repository The repository to be set up
     * @param resources The resources which should get added to the template
     * @param prefix The prefix for the path to which the resources should get copied to
     * @param templateName The name of the template
     * @param programmingExercise The related programming exercise for which the template should get created
     * @throws Exception If anything goes wrong
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

            if (!programmingExercise.hasSequentialTestRuns()) {
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
                List<String> sequentialTestTasks = new ArrayList<>();
                sequentialTestTasks.add("structural");
                sequentialTestTasks.add("behavior");

                sectionsMap.put("non-sequential", false);
                sectionsMap.put("sequential", true);

                fileService.replacePlaceholderSections(Paths.get(repository.getLocalPath().toAbsolutePath().toString(), "pom.xml").toAbsolutePath().toString(), sectionsMap);

                for (String buildStage : sequentialTestTasks) {

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
            commitAndPushRepository(repository, templateName);
        }
        else {
            // If there is no special test structure for a programming language, just copy all the test files.
            setupTemplateAndPush(repository, resources, prefix, templateName, programmingExercise);
        }
    }

    /**
     * Replace placeholders in repository files (e.g. ${placeholder}).
     * 
     * @param programmingExercise The related programming exercise
     * @param repository The repository in which the placeholders should get replaced
     * @throws IOException If replacing the directory name, or file variables throws an exception
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
     * @param repository The repository to which the changes should get pushed
     * @param templateName The template name which should be put in the commit message
     * @throws GitAPIException If committing, or pushing to the repo throws an exception
     */
    public void commitAndPushRepository(Repository repository, String templateName) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, templateName + "-Template pushed by Artemis");
        repository.setFiles(null); // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the template Participation
     *
     * @param participation The template participation
     * @return The ProgrammingExercise where the given Participation is the template Participation
     */
    public ProgrammingExercise getExercise(TemplateProgrammingExerciseParticipation participation) {
        return programmingExerciseRepository.findOneByTemplateParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution Participation
     *
     * @param participation The solution participation
     * @return The ProgrammingExercise where the given Participation is the solution Participation
     */
    public ProgrammingExercise getExercise(SolutionProgrammingExerciseParticipation participation) {
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
     * Find a programming exercise by its id.
     * 
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findById(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found");
        }
    }

    /**
     * Find a programming exercise by its id, including all test cases
     *
     * @param id of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     * @throws IllegalAccessException  the retriever does not have the permissions to fetch information related to the programming exercise.
     */
    public ProgrammingExercise findByIdWithTestCases(Long id) throws EntityNotFoundException, IllegalAccessException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findByIdWithTestCases(id);
        if (programmingExercise.isPresent()) {
            Course course = programmingExercise.get().getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                throw new IllegalAccessException();
            }
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found");
        }
    }

    /**
     * This method saves the template and solution participations of the programming exercise
     *
     * @param programmingExercise The programming exercise for which the participations should get saved
     */
    public void saveParticipations(ProgrammingExercise programmingExercise) {
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();

        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
    }

    /**
     * Squash all commits of the given repository into one.
     * 
     * @param repoUrl of the repository to squash.
     * @throws InterruptedException If the checkout fails
     * @throws GitAPIException If the checkout fails
     */
    public void squashAllCommitsOfRepositoryIntoOne(URL repoUrl) throws InterruptedException, GitAPIException {
        Repository exerciseRepository = gitService.getOrCheckoutRepository(repoUrl, true);
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
     * @throws IOException If the URLs cannot be converted to actual {@link Path paths}
     * @throws InterruptedException If the checkout fails
     */
    public boolean generateStructureOracleFile(URL solutionRepoURL, URL exerciseRepoURL, URL testRepoURL, String testsPath)
            throws IOException, GitAPIException, InterruptedException {
        Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepoURL, true);
        Repository exerciseRepository = gitService.getOrCheckoutRepository(exerciseRepoURL, true);
        Repository testRepository = gitService.getOrCheckoutRepository(testRepoURL, true);

        gitService.resetToOriginMaster(solutionRepository);
        gitService.pullIgnoreConflicts(solutionRepository);
        gitService.resetToOriginMaster(exerciseRepository);
        gitService.pullIgnoreConflicts(exerciseRepository);
        gitService.resetToOriginMaster(testRepository);
        gitService.pullIgnoreConflicts(testRepository);

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

    /**
     * Delete a programming exercise, including its template and solution participations.
     *
     * @param programmingExercise to delete.
     * @param deleteBaseReposBuildPlans if true will also delete build plans and projects.
     */
    @Transactional
    public void delete(ProgrammingExercise programmingExercise, boolean deleteBaseReposBuildPlans) {
        if (deleteBaseReposBuildPlans) {
            if (programmingExercise.getTemplateBuildPlanId() != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExercise.getTemplateBuildPlanId());
            }
            if (programmingExercise.getSolutionBuildPlanId() != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExercise.getSolutionBuildPlanId());
            }
            continuousIntegrationService.get().deleteProject(programmingExercise.getProjectKey());

            if (programmingExercise.getTemplateRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(programmingExercise.getTemplateRepositoryUrlAsUrl());
                gitService.deleteLocalRepository(programmingExercise.getTemplateRepositoryUrlAsUrl());
            }
            if (programmingExercise.getSolutionRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(programmingExercise.getSolutionRepositoryUrlAsUrl());
                gitService.deleteLocalRepository(programmingExercise.getSolutionRepositoryUrlAsUrl());
            }
            if (programmingExercise.getTestRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(programmingExercise.getTestRepositoryUrlAsUrl());
                gitService.deleteLocalRepository(programmingExercise.getTestRepositoryUrlAsUrl());
            }
            versionControlService.get().deleteProject(programmingExercise.getProjectKey());
        }

        SolutionProgrammingExerciseParticipation solutionProgrammingExerciseParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateProgrammingExerciseParticipation = programmingExercise.getTemplateParticipation();
        participationService.deleteResultsAndSubmissionsOfParticipation(solutionProgrammingExerciseParticipation);
        participationService.deleteResultsAndSubmissionsOfParticipation(templateProgrammingExerciseParticipation);
        // This will also delete the template & solution participation.
        programmingExerciseRepository.delete(programmingExercise);
    }
}
