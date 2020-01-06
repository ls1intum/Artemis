package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.validation.constraints.NotNull;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.util.structureoraclegenerator.OracleGenerator;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final FileService fileService;

    private final GitService gitService;

    private final ExerciseHintService exerciseHintService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ParticipationService participationService;

    private final ResultRepository resultRepository;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ResourceLoader resourceLoader;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ExerciseService exerciseService;

    @Value("${artemis.repo-download-clone-path}")
    private String REPO_DOWNLOAD_CLONE_PATH;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, FileService fileService, GitService gitService,
            ExerciseHintService exerciseHintService, Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ParticipationService participationService,
            ResultRepository resultRepository, UserService userService, AuthorizationCheckService authCheckService, ResourceLoader resourceLoader,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, @Lazy ExerciseService exerciseService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.fileService = fileService;
        this.gitService = gitService;
        this.exerciseHintService = exerciseHintService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resourceLoader = resourceLoader;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.exerciseService = exerciseService;
    }

    /**
     * Adds the student id of the given student participation to the project name in all .project (Eclipse)
     * and pom.xml (Maven) files found in the given repository.
     *
     * @param repo The repository for which the student id should get added
     * @param programmingExercise The checked out exercise in the repository
     * @param participation The student participation for the student id, which should be added.
     */
    @Transactional
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
     * @return The newly setup exercise
     * @throws Exception If anything goes wrong
     */
    @Transactional
    public ProgrammingExercise setupProgrammingExercise(ProgrammingExercise programmingExercise) throws Exception {
        User user = userService.getUser();
        programmingExercise.generateAndSetProjectKey();
        String projectKey = programmingExercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();

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

        String templatePlanName = BuildPlanType.TEMPLATE.getName();
        String solutionPlanName = BuildPlanType.SOLUTION.getName();
        templateParticipation.setBuildPlanId(projectKey + "-" + templatePlanName); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(projectKey + "-" + solutionPlanName);
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, testRepoName).toString());

        // Save participations to get the ids required for the webhooks
        templateParticipation.setProgrammingExercise(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        templateParticipation = templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionParticipation = solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        URL exerciseRepoUrl = versionControlService.get().getCloneRepositoryUrl(projectKey, exerciseRepoName).getURL();
        URL testsRepoUrl = versionControlService.get().getCloneRepositoryUrl(projectKey, testRepoName).getURL();
        URL solutionRepoUrl = versionControlService.get().getCloneRepositoryUrl(projectKey, solutionRepoName).getURL();

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
            setupTemplateAndPush(exerciseRepo, exerciseResources, exercisePrefix, "Exercise", programmingExercise, user);
            setupTemplateAndPush(solutionRepo, solutionResources, solutionPrefix, "Solution", programmingExercise, user);
            setupTestTemplateAndPush(testRepo, testResources, testPrefix, "Test", programmingExercise, user);

        }
        catch (Exception ex) {
            // if any exception occurs, try to at least push an empty commit, so that the
            // repositories can be used by the build plans
            log.warn("An exception occurred while setting up the repositories", ex);
            gitService.commitAndPush(exerciseRepo, "Empty Setup by Artemis", user);
            gitService.commitAndPush(testRepo, "Empty Setup by Artemis", user);
            gitService.commitAndPush(solutionRepo, "Empty Setup by Artemis", user);
        }

        continuousIntegrationService.get().createProjectForExercise(programmingExercise);
        // template build plan
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, templatePlanName, exerciseRepoUrl, testsRepoUrl);
        // solution build plan
        continuousIntegrationService.get().createBuildPlanForExercise(programmingExercise, solutionPlanName, solutionRepoUrl, testsRepoUrl);

        // Give appropriate permissions for CI projects
        continuousIntegrationService.get().removeAllDefaultProjectPermissions(projectKey);
        giveCIProjectPermissions(programmingExercise);

        // save to get the id required for the webhook
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        // The creation of the webhooks must occur after the initial push, because the participation is
        // not yet saved in the database, so we cannot save the submission accordingly (see ProgrammingSubmissionService.notifyPush)
        versionControlService.get().addWebHooksForExercise(programmingExercise);

        return programmingExercise;
    }

    /**
     * This methods sets the values (initialization date and initialization state) of the template and solution participation
     *
     * @param programmingExercise The programming exercise
     */
    @Transactional
    public void initParticipations(ProgrammingExercise programmingExercise) {

        Participation solutionParticipation = programmingExercise.getSolutionParticipation();
        Participation templateParticipation = programmingExercise.getTemplateParticipation();

        solutionParticipation.setInitializationState(InitializationState.INITIALIZED);
        templateParticipation.setInitializationState(InitializationState.INITIALIZED);
        solutionParticipation.setInitializationDate(ZonedDateTime.now());
        templateParticipation.setInitializationDate(ZonedDateTime.now());
    }

    // Copy template and push, if no file is in the directory
    private void setupTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise, User user)
            throws Exception {
        if (gitService.listFiles(repository).size() == 0) { // Only copy template if repo is empty
            fileService.copyResources(resources, prefix, repository.getLocalPath().toAbsolutePath().toString(), true);
            replacePlaceholders(programmingExercise, repository);
            commitAndPushRepository(repository, templateName, user);
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
     * @param user the user who has initiated the generation of the programming exercise
     * @throws Exception If anything goes wrong
     */
    private void setupTestTemplateAndPush(Repository repository, Resource[] resources, String prefix, String templateName, ProgrammingExercise programmingExercise, User user)
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
            commitAndPushRepository(repository, templateName, user);
        }
        else {
            // If there is no special test structure for a programming language, just copy all the test files.
            setupTemplateAndPush(repository, resources, prefix, templateName, programmingExercise, user);
        }
    }

    /**
     * Replace placeholders in repository files (e.g. ${placeholder}).
     * 
     * @param programmingExercise The related programming exercise
     * @param repository The repository in which the placeholders should get replaced
     * @throws IOException If replacing the directory name, or file variables throws an exception
     */
    @Transactional
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
     * @param user the user who has initiated the generation of the programming exercise
     */
    @Transactional
    public void commitAndPushRepository(Repository repository, String templateName, User user) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, templateName + "-Template pushed by Artemis", user);
        repository.setFiles(null); // Clear cache to avoid multiple commits when Artemis server is not restarted between attempts
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the template Participation
     *
     * @param participation The template participation
     * @return The ProgrammingExercise where the given Participation is the template Participation
     */
    @Transactional
    public ProgrammingExercise getExercise(TemplateProgrammingExerciseParticipation participation) {
        return programmingExerciseRepository.findOneByTemplateParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution Participation
     *
     * @param participation The solution participation
     * @return The ProgrammingExercise where the given Participation is the solution Participation
     */
    @Transactional
    public ProgrammingExercise getExercise(SolutionProgrammingExerciseParticipation participation) {
        return programmingExerciseRepository.findOneBySolutionParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution or template Participation
     *
     * @param participation The solution or template participation
     * @return The ProgrammingExercise where the given Participation is the solution or template Participation
     */
    @Transactional
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
    @Transactional
    public ProgrammingExercise findById(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
        }
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded studentParticipations.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findByIdWithEagerStudentParticipations(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findByIdWithEagerParticipations(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found");
        }
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded studentParticipations and submissions
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findByIdWithEagerStudentParticipationsAndSubmissions(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findByIdWithEagerParticipationsAndSubmissions(programmingExerciseId);
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
    @Transactional
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
    @Transactional
    public void saveParticipations(ProgrammingExercise programmingExercise) {
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();

        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
    }

    /**
     * Combine all commits of the given repository into one.
     * 
     * @param repoUrl of the repository to combine.
     * @throws InterruptedException If the checkout fails
     * @throws GitAPIException If the checkout fails
     */
    @Transactional
    public void combineAllCommitsOfRepositoryIntoOne(URL repoUrl) throws InterruptedException, GitAPIException {
        Repository exerciseRepository = gitService.getOrCheckoutRepository(repoUrl, true);
        gitService.combineAllCommitsIntoInitialCommit(exerciseRepository);
    }

    /**
     * Updates the problem statement of the given programming exercise.
     *
     * @param programmingExerciseId ProgrammingExercise Id.
     * @param problemStatement markdown of the problem statement.
     * @return the updated ProgrammingExercise object.
     * @throws EntityNotFoundException if there is no ProgrammingExercise for the given id.
     * @throws IllegalAccessException if the user does not have permissions to access the ProgrammingExercise.
     */
    @Transactional
    public ProgrammingExercise updateProblemStatement(Long programmingExerciseId, String problemStatement) throws EntityNotFoundException, IllegalAccessException {
        Optional<ProgrammingExercise> programmingExerciseOpt = programmingExerciseRepository.findById(programmingExerciseId);
        if (programmingExerciseOpt.isEmpty()) {
            throw new EntityNotFoundException("Programming exercise not found with id: " + programmingExerciseId);
        }
        ProgrammingExercise programmingExercise = programmingExerciseOpt.get();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise, user)) {
            throw new IllegalAccessException("User with login " + user.getLogin() + " is not authorized to access programming exercise with id: " + programmingExerciseId);
        }
        programmingExercise.setProblemStatement(problemStatement);
        return programmingExercise;
    }

    /**
     * This method calls the StructureOracleGenerator, generates the string out of the JSON representation of the structure oracle of the programming exercise and returns true if
     * the file was updated or generated, false otherwise. This can happen if the contents of the file have not changed.
     *
     * @param solutionRepoURL The URL of the solution repository.
     * @param exerciseRepoURL The URL of the exercise repository.
     * @param testRepoURL     The URL of the tests repository.
     * @param testsPath       The path to the tests folder, e.g. the path inside the repository where the structure oracle file will be saved in.
     * @param user            The user who has initiated the action
     * @return True, if the structure oracle was successfully generated or updated, false if no changes to the file were made.
     * @throws IOException If the URLs cannot be converted to actual {@link Path paths}
     * @throws InterruptedException If the checkout fails
     * @throws GitAPIException If the checkout fails
     */
    @Transactional
    public boolean generateStructureOracleFile(URL solutionRepoURL, URL exerciseRepoURL, URL testRepoURL, String testsPath, User user)
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

        String structureOracleJSON = OracleGenerator.generateStructureOracleJSON(solutionRepositoryPath, exerciseRepositoryPath);

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
                gitService.commitAndPush(testRepository, "Generate the structure oracle file.", user);
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
                    gitService.commitAndPush(testRepository, "Update the structure oracle file.", user);
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
     * @param programmingExerciseId id of the programming exercise to delete.
     * @param deleteBaseReposBuildPlans if true will also delete build plans and projects.
     */
    @Transactional
    public void delete(Long programmingExerciseId, boolean deleteBaseReposBuildPlans) {
        // TODO: This method does not accept a programming exercise to solve issues with nested Transactions.
        // It would be good to refactor the delete calls and move the validity checks down from the resources to the service methods (e.g. EntityNotFound).
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findById(programmingExerciseId).get();
        if (deleteBaseReposBuildPlans) {

            final var templateBuildPlanId = programmingExercise.getTemplateBuildPlanId();
            if (templateBuildPlanId != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanId);
            }
            final var solutionBuildPlanId = programmingExercise.getSolutionBuildPlanId();
            if (solutionBuildPlanId != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanId);
            }
            continuousIntegrationService.get().deleteProject(programmingExercise.getProjectKey());

            if (programmingExercise.getTemplateRepositoryUrl() != null) {
                final var templateRepositoryUrlAsUrl = programmingExercise.getTemplateRepositoryUrlAsUrl();
                versionControlService.get().deleteRepository(templateRepositoryUrlAsUrl);
                gitService.deleteLocalRepository(templateRepositoryUrlAsUrl);
            }
            if (programmingExercise.getSolutionRepositoryUrl() != null) {
                final var solutionRepositoryUrlAsUrl = programmingExercise.getSolutionRepositoryUrlAsUrl();
                versionControlService.get().deleteRepository(solutionRepositoryUrlAsUrl);
                gitService.deleteLocalRepository(solutionRepositoryUrlAsUrl);
            }
            if (programmingExercise.getTestRepositoryUrl() != null) {
                final var testRepositoryUrlAsUrl = programmingExercise.getTestRepositoryUrlAsUrl();
                versionControlService.get().deleteRepository(testRepositoryUrlAsUrl);
                gitService.deleteLocalRepository(testRepositoryUrlAsUrl);
            }
            versionControlService.get().deleteProject(programmingExercise.getProjectKey());
        }

        SolutionProgrammingExerciseParticipation solutionProgrammingExerciseParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateProgrammingExerciseParticipation = programmingExercise.getTemplateParticipation();
        if (solutionProgrammingExerciseParticipation != null) {
            participationService.deleteResultsAndSubmissionsOfParticipation(solutionProgrammingExerciseParticipation.getId());
        }
        if (templateProgrammingExerciseParticipation != null) {
            participationService.deleteResultsAndSubmissionsOfParticipation(templateProgrammingExerciseParticipation.getId());
        }
        // This will also delete the template & solution participation.
        programmingExerciseRepository.delete(programmingExercise);
    }

    /**
     * Returns the list of programming exercises with a buildAndTestStudentSubmissionsAfterDueDate in future.
     * @return List<ProgrammingExercise>
     */
    public List<ProgrammingExercise> findAllWithBuildAndTestAfterDueDateInFuture() {
        return programmingExerciseRepository.findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(ZonedDateTime.now());
    }

    public boolean hasAtLeastOneStudentResult(ProgrammingExercise programmingExercise) {
        // Is true if the exercise is released and has at least one result.
        // TODO: We can't use the resultService here due to a circular dependency issue.
        return resultRepository.existsByParticipation_ExerciseId(programmingExercise.getId());
    }

    public ProgrammingExercise save(ProgrammingExercise programmingExercise) {
        return programmingExerciseRepository.save(programmingExercise);
    }

    /**
     * Search for all programming exercises fitting a {@link PageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ProgrammingExercise> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        var sorting = Sort.by(ProgrammingExercise.ProgrammingExerciseSearchColumn.valueOf(search.getSortedColumn()).getMappedColumnName());
        sorting = search.getSortingOrder() == SortingOrder.ASCENDING ? sorting.ascending() : sorting.descending();
        final var sorted = PageRequest.of(search.getPage(), search.getPageSize(), sorting);
        final var searchTerm = search.getSearchTerm();

        final var exercisePage = authCheckService.isAdmin()
                ? programmingExerciseRepository.findByTitleIgnoreCaseContainingAndShortNameNotNullOrCourse_TitleIgnoreCaseContainingAndShortNameNotNull(searchTerm, searchTerm,
                        sorted)
                : programmingExerciseRepository.findByTitleInExerciseOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), sorted);

        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Imports a programming exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except for repositories, or build plans on a remote version control server, or
     * continuous integration server. <br>
     * There are however, a couple of things that will never get copied:
     * <ul>
     *     <li>The ID</li>
     *     <li>The template and solution participation</li>
     *     <li>The number of complaints, assessments and more feedback requests</li>
     *     <li>The tutor/student participations</li>
     *     <li>The questions asked by students</li>
     *     <li>The example submissions</li>
     * </ul>
     *
     * @param templateExercise The template exercise which should get imported
     * @param newExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @Transactional
    public ProgrammingExercise importProgrammingExerciseBasis(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        // Set values we don't want to copy to null
        setupExerciseForImport(newExercise);
        newExercise.generateAndSetProjectKey();
        final var projectKey = newExercise.getProjectKey();
        final var templatePlanName = BuildPlanType.TEMPLATE.getName();
        final var solutionPlanName = BuildPlanType.SOLUTION.getName();

        programmingExerciseParticipationService.setupInitialSolutionParticipation(newExercise, projectKey, solutionPlanName);
        programmingExerciseParticipationService.setupInitalTemplateParticipation(newExercise, projectKey, templatePlanName);
        setupTestRepository(newExercise, projectKey);
        initParticipations(newExercise);

        // Hints and test cases
        exerciseHintService.copyExerciseHints(templateExercise, newExercise);
        programmingExerciseRepository.save(newExercise);
        importTestCases(templateExercise, newExercise);

        return newExercise;
    }

    /**
     * Import all base repositories from one exercise. These include the template, the solution and the test
     * repository. Participation repositories from students or tutors will not get copied!
     *
     * @param templateExercise The template exercise having a reference to all base repositories
     * @param newExercise The new exercise without any repositories
     */
    public void importRepositories(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        final var targetProjectKey = newExercise.getProjectKey();
        final var sourceProjectKey = templateExercise.getProjectKey();
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();

        // First, create a new project for our imported exercise
        versionControlService.get().createProjectForExercise(newExercise);
        // Copy all repositories
        final var reposToCopy = List.of(Pair.of(RepositoryType.TEMPLATE, templateExercise.getTemplateRepositoryName()),
                Pair.of(RepositoryType.SOLUTION, templateExercise.getSolutionRepositoryName()), Pair.of(RepositoryType.TESTS, templateExercise.getTestRepositoryName()));
        reposToCopy.forEach(repo -> versionControlService.get().copyRepository(sourceProjectKey, repo.getSecond(), targetProjectKey, repo.getFirst().getName()));
        // Add the necessary hooks notifying Artemis about changes after commits have been pushed
        versionControlService.get().addWebHooksForExercise(newExercise);
    }

    /**
     * Imports all base build plans for an exercise. These include the template and the solution build plan, <b>not</b>
     * any participation plans!
     *
     * @param templateExercise The template exercise which plans should get copied
     * @param newExercise The new exercise to which all plans should get copied
     * @throws HttpException If the copied build plans could not get triggered
     */
    public void importBuildPlans(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) throws HttpException {
        final var templateParticipation = newExercise.getTemplateParticipation();
        final var solutionParticipation = newExercise.getSolutionParticipation();
        final var templatePlanName = BuildPlanType.TEMPLATE.getName();
        final var solutionPlanName = BuildPlanType.SOLUTION.getName();
        final var templateKey = templateExercise.getProjectKey();
        final var targetKey = newExercise.getProjectKey();
        final var targetName = newExercise.getCourse().getShortName().toUpperCase() + " " + newExercise.getTitle();
        final var targetExerciseProjectKey = newExercise.getProjectKey();

        // Clone all build plans, enable them and setup the initial participations, i.e. setting the correct repo URLs and
        // running the plan for the first time
        continuousIntegrationService.get().copyBuildPlan(templateKey, templatePlanName, targetKey, targetName, templatePlanName);
        continuousIntegrationService.get().copyBuildPlan(templateKey, solutionPlanName, targetKey, targetName, solutionPlanName);
        giveCIProjectPermissions(newExercise);
        continuousIntegrationService.get().enablePlan(targetExerciseProjectKey, templateParticipation.getBuildPlanId());
        continuousIntegrationService.get().enablePlan(targetExerciseProjectKey, solutionParticipation.getBuildPlanId());

        // update 2 repositories for the template (BASE) build plan
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, templateParticipation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTemplateRepositoryUrl(), Optional.of(List.of(ASSIGNMENT_REPO_NAME)));
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, templateParticipation.getBuildPlanId(), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUrl(), Optional.empty());

        // update 2 repositories for the solution (SOLUTION) build plan
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, solutionParticipation.getBuildPlanId(), ASSIGNMENT_REPO_NAME, targetExerciseProjectKey,
                newExercise.getSolutionRepositoryUrl(), Optional.empty());
        continuousIntegrationService.get().updatePlanRepository(targetExerciseProjectKey, solutionParticipation.getBuildPlanId(), TEST_REPO_NAME, targetExerciseProjectKey,
                newExercise.getTestRepositoryUrl(), Optional.empty());

        try {
            continuousIntegrationService.get().triggerBuild(templateParticipation);
            continuousIntegrationService.get().triggerBuild(solutionParticipation);
        }
        catch (HttpException e) {
            log.error("Unable to trigger imported build plans", e);
            throw e;
        }
    }

    private void giveCIProjectPermissions(ProgrammingExercise exercise) {
        final var instructorGroup = exercise.getCourse().getInstructorGroupName();
        final var teachingAssistantGroup = exercise.getCourse().getTeachingAssistantGroupName();

        continuousIntegrationService.get().giveProjectPermissions(exercise.getProjectKey(), List.of(instructorGroup),
                List.of(CIPermission.CREATE, CIPermission.READ, CIPermission.ADMIN));
        continuousIntegrationService.get().giveProjectPermissions(exercise.getProjectKey(), List.of(teachingAssistantGroup), List.of(CIPermission.READ));
    }

    /**
     * Remove the write permissions for all students for their programming exercise repository.
     * They will still be able to read the code, but won't be able to change it.
     *
     * Requests are executed in batches so that the VCS is not overloaded with requests.
     *
     * @param programmingExerciseId     ProgrammingExercise id.
     * @return a list of participations for which the locking operation has failed. If everything went as expected, this should be an empty list.
     * @throws EntityNotFoundException  if the programming exercise can't be found.
     */
    public List<ProgrammingExerciseStudentParticipation> removeWritePermissionsFromAllStudentRepositories(Long programmingExerciseId) throws EntityNotFoundException {
        log.info("Invoking scheduled task 'remove write permissions from all student repositories' for programming exercise with id " + programmingExerciseId + ".");

        ProgrammingExercise programmingExercise = findByIdWithEagerStudentParticipations(programmingExerciseId);
        List<ProgrammingExerciseStudentParticipation> failedLockOperations = new LinkedList<>();

        int index = 0;
        for (StudentParticipation studentParticipation : programmingExercise.getStudentParticipations()) {
            // Execute requests in batches instead all at once.
            if (index > 0 && index % EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE == 0) {
                try {
                    log.info("Sleep for {}s during removeWritePermissionsFromAllStudentRepositories", EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS / 1000);
                    Thread.sleep(EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS);
                }
                catch (InterruptedException ex) {
                    log.error("Exception encountered when pausing before locking the student repositories for exercise " + programmingExerciseId, ex);
                }
            }

            ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            try {
                versionControlService.get().setRepositoryPermissionsToReadOnly(programmingExerciseStudentParticipation.getRepositoryUrlAsUrl(), programmingExercise.getProjectKey(),
                        programmingExerciseStudentParticipation.getStudent().getLogin());
            }
            catch (Exception e) {
                log.error("Removing write permissions failed for programming exercise with id " + programmingExerciseId + " for student repository with participation id "
                        + studentParticipation.getId());
                failedLockOperations.add(programmingExerciseStudentParticipation);
            }
            index++;
        }
        return failedLockOperations;
    }

    /**
     * Check if the repository of the given participation is locked.
     * This is the case when the participation is a ProgrammingExerciseStudentParticipation, the buildAndTestAfterDueDate of the exercise is set and the due date has passed.
     *
     * Locked means that the student can't make any changes to their repository anymore. While we can control this easily in the remote VCS, we need to check this manually for the local repository on the Artemis server.
     *
     * @param participation ProgrammingExerciseParticipation
     * @return true if repository is locked, false if not.
     */
    public boolean isParticipationRepositoryLocked(ProgrammingExerciseParticipation participation) {
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
            return programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null && programmingExercise.getDueDate().isBefore(ZonedDateTime.now());
        }
        return false;
    }

    /**
     * Copied test cases from one exercise to another. The test cases will get new IDs, thus being saved as a new entity.
     * The remaining contents stay the same, especially the weights.
     *
     * @param templateExercise The template exercise which test cases should get copied
     * @param targetExercise The new exercise to which all test cases should get copied to
     */
    private void importTestCases(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise) {
        targetExercise.setTestCases(templateExercise.getTestCases().stream().map(testCase -> {
            final var copy = new ProgrammingExerciseTestCase();

            // Copy everything except for the referenced exercise
            copy.setActive(testCase.isActive());
            copy.setAfterDueDate(testCase.isAfterDueDate());
            copy.setTestName(testCase.getTestName());
            copy.setWeight(testCase.getWeight());
            copy.setExercise(targetExercise);
            programmingExerciseTestCaseRepository.save(copy);
            return copy;
        }).collect(Collectors.toSet()));
    }

    /**
     * Sets up the test repository for a new exercise by setting the repository URL. This does not create the actual
     * repository on the version control server!
     *
     * @param newExercise
     * @param projectKey
     */
    private void setupTestRepository(ProgrammingExercise newExercise, String projectKey) {
        final var testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        newExercise.setTestRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, testRepoName).toString());
    }

    /**
     * Sets up a new exercise for importing it by setting all values, that should either never get imported, or
     * for which we should create new entities (e.g. test cases) to null. This ensures that we do not copy
     * anything by accident.
     *
     * @param newExercise
     */
    private void setupExerciseForImport(ProgrammingExercise newExercise) {
        newExercise.setId(null);
        newExercise.setTemplateParticipation(null);
        newExercise.setSolutionParticipation(null);
        newExercise.setExerciseHints(null);
        newExercise.setTestCases(null);
        newExercise.setAttachments(null);
        newExercise.setNumberOfMoreFeedbackRequests(null);
        newExercise.setNumberOfComplaints(null);
        newExercise.setNumberOfAssessments(null);
        newExercise.setTutorParticipations(null);
        newExercise.setExampleSubmissions(null);
        newExercise.setStudentQuestions(null);
        newExercise.setStudentParticipations(null);
    }

    /**
     * Get participations of coding exercises of a requested list of students packed together in one zip file.
     *
     * @param exerciseId the id of the exercise entity
     * @param participations participations that should be exported
     * @param repositoryExportOptions the options that should be used for the export
     * @return a zip file containing all requested participations
     */
    @Transactional(readOnly = true)
    public java.io.File exportStudentRepositories(Long exerciseId, @NotNull List<ProgrammingExerciseStudentParticipation> participations,
            RepositoryExportOptionsDTO repositoryExportOptions) {
        // The downloaded repos should be cloned into another path in order to not interfere with the repo used by the student
        String repoDownloadClonePath = REPO_DOWNLOAD_CLONE_PATH;

        ProgrammingExercise programmingExercise = (ProgrammingExercise) exerciseService.findOne(exerciseId);

        if (repositoryExportOptions.isExportAllStudents()) {
            log.info("Request to export all student repositories of programming exercise " + exerciseId + " with title '" + programmingExercise.getTitle() + "'");
        }
        else {
            log.info("Request to export the repositories of programming exercise " + exerciseId + " with title '" + programmingExercise.getTitle() + "' of the following students: "
                    + participations.stream().map(p -> p.getStudent().getLogin()).collect(Collectors.joining(", ")));
        }

        List<Path> zippedRepoFiles = new ArrayList<>();
        for (ProgrammingExerciseStudentParticipation participation : participations) {
            Repository repo = null;
            try {
                if (participation.getRepositoryUrlAsUrl() == null) {
                    log.warn("Ignore participation " + participation.getId() + " for export, because its repository URL is null");
                    continue;
                }
                repo = gitService.getOrCheckoutRepository(participation, repoDownloadClonePath);
                gitService.resetToOriginMaster(repo); // start with clean state

                if (repositoryExportOptions.isFilterLateSubmissions() && repositoryExportOptions.getFilterLateSubmissionsDate() != null) {
                    log.debug("Filter late submissions for participation {}", participation.toString());
                    Optional<Submission> lastValidSubmission = participation.getSubmissions().stream()
                            .filter(s -> s.getSubmissionDate() != null && s.getSubmissionDate().isBefore(repositoryExportOptions.getFilterLateSubmissionsDate()))
                            .max(Comparator.comparing(Submission::getSubmissionDate));

                    gitService.filterLateSubmissions(repo, lastValidSubmission, repositoryExportOptions.getFilterLateSubmissionsDate());
                }

                if (repositoryExportOptions.isAddStudentName()) {
                    log.debug("Adding student name to participation {}", participation.toString());
                    addStudentIdToProjectName(repo, programmingExercise, participation);
                }

                if (repositoryExportOptions.isCombineStudentCommits()) {
                    log.debug("Combining commits for participation {}", participation.toString());
                    gitService.combineAllStudentCommits(repo, programmingExercise);
                }

                if (repositoryExportOptions.isNormalizeCodeStyle()) {
                    try {
                        log.debug("Normalizing code style for participation {}", participation.toString());
                        fileService.normalizeLineEndingsDirectory(repo.getLocalPath().toString());
                        fileService.convertToUTF8Directory(repo.getLocalPath().toString());
                    }
                    catch (Exception ex) {
                        log.warn("Cannot normalize code style in the repo " + repo.getLocalPath() + " due to the following exception: " + ex.getMessage());
                    }
                }

                log.debug("Create temporary zip file for repository " + repo.getLocalPath().toString());
                Path zippedRepoFile = gitService.zipRepository(repo, repoDownloadClonePath);
                zippedRepoFiles.add(zippedRepoFile);
            }
            catch (IOException | GitException | GitAPIException | InterruptedException ex) {
                log.error("export student repository " + participation.getRepositoryUrlAsUrl() + " in exercise '" + programmingExercise.getTitle() + "' did not work as expected: "
                        + ex.getMessage());
            }
            finally {
                // we do some cleanup here to prevent future errors with file handling
                // We can always delete the repository as it won't be used by the student (separate path)
                if (repo != null) {
                    try {
                        log.debug("Delete temporary repository " + repo.getLocalPath().toString());
                        gitService.deleteLocalRepository(participation, repoDownloadClonePath);
                    }
                    catch (Exception ex) {
                        log.warn("Could not delete temporary repository " + repo.getLocalPath().toString() + ": " + ex.getMessage());
                    }
                }
            }
        }
        if (zippedRepoFiles.isEmpty()) {
            log.warn("The zip file could not be created. Ignoring the request to export repositories for exercise " + programmingExercise.getTitle());
            return null;
        }
        try {
            // create a large zip file with all zipped repos and provide it for download
            log.debug("Create zip file for all repositories");
            Path zipFilePath = Paths.get(zippedRepoFiles.get(0).getParent().toString(),
                    programmingExercise.getCourse().getShortName() + "-" + programmingExercise.getShortName() + "-" + System.currentTimeMillis() + ".zip");
            createZipFile(zipFilePath, zippedRepoFiles);
            scheduleForDeletion(zipFilePath, 15);
            log.info("Export student repositories of programming exercise " + exerciseId + " with title '" + programmingExercise.getTitle() + "' was successful.");
            return new java.io.File(zipFilePath.toString());
        }
        catch (IOException ex) {
            log.error("Export students repositories for exercise '" + programmingExercise.getTitle() + "' did not work as expected: " + ex.getMessage());
        }
        finally {
            // we do some cleanup here to prevent future errors with file handling
            log.debug("Delete all temporary zip repo files");
            // delete the temporary zipped repo files
            for (Path zippedRepoFile : zippedRepoFiles) {
                try {
                    Files.delete(zippedRepoFile);
                }
                catch (Exception ex) {
                    log.warn("Could not delete file " + zippedRepoFile + ". Error message: " + ex.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Create a zipfile of the given paths and save it in the zipFilePath
     *
     * @param zipFilePath path where the zipfile should be saved
     * @param paths the paths that should be zipped
     * @throws IOException if an error occured while zipping
     */
    private void createZipFile(Path zipFilePath, List<Path> paths) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            paths.stream().filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(path.toString());
                try {
                    zipOutputStream.putNextEntry(zipEntry);
                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
                catch (Exception e) {
                    log.error("Create zip file error", e);
                }
            });
        }
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of programming submissions which should be assessed
     */
    public long countSubmissions(Long exerciseId) {
        return programmingExerciseRepository.countSubmissions(exerciseId);
    }

    /**
     * @param courseId the course we are interested in
     * @return the number of programming submissions which should be assessed, so we ignore the ones after the exercise due date
     */
    public long countSubmissionsToAssessByCourseId(Long courseId) {
        return programmingExerciseRepository.countByCourseIdSubmittedBeforeDueDate(courseId);
    }

    private Map<Path, ScheduledFuture> futures = new HashMap<>();

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private static final TimeUnit MINUTES = TimeUnit.MINUTES; // your time unit

    /**
     * Schedule the deletion of the given path with a given delay
     *
     * @param path The path that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    private void scheduleForDeletion(Path path, long delayInMinutes) {
        ScheduledFuture future = executor.schedule(() -> {
            try {
                log.info("Delete file " + path);
                Files.delete(path);
                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the file " + path + " did not work", e);
            }
        }, delayInMinutes, MINUTES);

        futures.put(path, future);
    }
}
