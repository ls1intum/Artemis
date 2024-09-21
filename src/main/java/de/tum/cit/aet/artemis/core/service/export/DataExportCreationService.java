package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.export.DataExportExerciseCreationService.CSV_FILE_EXTENSION;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.service.DataExportScienceEventService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.DataExportState;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ArtemisMailException;
import de.tum.cit.aet.artemis.core.repository.DataExportRepository;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.service.user.UserService;

/**
 * A service to create data exports for users
 * This service is responsible for creating the data export, delegating most tasks to the {@link DataExportExerciseCreationService} and {@link DataExportExamCreationService}
 * and notifying the user about the creation.
 */
@Profile(PROFILE_CORE)
@Service
public class DataExportCreationService {

    private static final String ZIP_FILE_EXTENSION = ".zip";

    private static final Logger log = LoggerFactory.getLogger(DataExportCreationService.class);

    private final Path dataExportsPath;

    private final ZipFileService zipFileService;

    private final FileService fileService;

    private final SingleUserNotificationService singleUserNotificationService;

    private final DataExportRepository dataExportRepository;

    private final MailService mailService;

    private final UserService userService;

    private final DataExportExerciseCreationService dataExportExerciseCreationService;

    private final DataExportExamCreationService dataExportExamCreationService;

    private final DataExportCommunicationDataService dataExportCommunicationDataService;

    private final DataExportScienceEventService dataExportScienceEventService;

    private final ResourceLoaderService resourceLoaderService;

    public DataExportCreationService(@Value("${artemis.data-export-path:./data-exports}") Path dataExportsPath, ZipFileService zipFileService, FileService fileService,
            SingleUserNotificationService singleUserNotificationService, DataExportRepository dataExportRepository, MailService mailService, UserService userService,
            DataExportExerciseCreationService dataExportExerciseCreationService, DataExportExamCreationService dataExportExamCreationService,
            DataExportCommunicationDataService dataExportCommunicationDataService, DataExportScienceEventService dataExportScienceEventService,
            ResourceLoaderService resourceLoaderService) {
        this.zipFileService = zipFileService;
        this.fileService = fileService;
        this.singleUserNotificationService = singleUserNotificationService;
        this.dataExportRepository = dataExportRepository;
        this.mailService = mailService;
        this.userService = userService;
        this.dataExportExerciseCreationService = dataExportExerciseCreationService;
        this.dataExportExamCreationService = dataExportExamCreationService;
        this.dataExportCommunicationDataService = dataExportCommunicationDataService;
        this.dataExportScienceEventService = dataExportScienceEventService;
        this.dataExportsPath = dataExportsPath;
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Creates the data export for the given user.
     * Retrieves all courses and exercises the user has participated in from the database.
     *
     * @param dataExport the data export to be created
     **/
    private DataExport createDataExportWithContent(DataExport dataExport) throws IOException, URISyntaxException {
        log.info("Creating data export for user {}", dataExport.getUser().getLogin());
        var userId = dataExport.getUser().getId();
        var user = dataExport.getUser();
        var workingDirectory = prepareDataExport(dataExport);
        dataExportExerciseCreationService.createExercisesExport(workingDirectory, user);
        dataExportExamCreationService.createExportForExams(userId, workingDirectory);
        dataExportCommunicationDataService.createCommunicationDataExport(userId, workingDirectory);
        dataExportScienceEventService.createScienceEventExport(user.getLogin(), workingDirectory);
        addGeneralUserInformation(user, workingDirectory);
        addReadmeFile(workingDirectory);
        var dataExportPath = createDataExportZipFile(user.getLogin(), workingDirectory);
        return finishDataExportCreation(dataExport, dataExportPath);
    }

    /**
     * Adds a markdown file with the title README.md to the data export.
     * <p>
     * This file contains information Art. 15 GDPR requires us to provide to the user.
     * The file is retrieved from the resources folder.
     * The file is added to the root of the data export.
     *
     * @param workingDirectory the directory in which the data export is created
     * @throws IOException        if the file could not be copied
     * @throws URISyntaxException if the resource file path is invalid
     */
    private void addReadmeFile(Path workingDirectory) throws IOException, URISyntaxException {
        var readmeInDataExportPath = workingDirectory.resolve("README.md");
        var readmeTemplatePath = Path.of("templates", "dataexport", "README.md");
        FileUtils.copyFile(resourceLoaderService.getResourceFilePath(readmeTemplatePath).toFile(), readmeInDataExportPath.toFile());
    }

    /**
     * Creates the data export for the given user.
     * <p>
     * This includes creation of the export and notifying the user about the creation.
     *
     * @param dataExport the data export to be created
     * @return true if the export was successful, false otherwise
     */
    public boolean createDataExport(DataExport dataExport) {
        try {
            dataExport = createDataExportWithContent(dataExport);
        }
        catch (Exception e) {
            log.error("Error while creating data export for user {}", dataExport.getUser().getLogin(), e);
            handleCreationFailure(dataExport, e);
            return false;
        }
        // the data export should be marked as successful even if the email cannot be sent.
        // The user still receives a webapp notification, that's why we wrap the following block in a try-catch
        try {
            singleUserNotificationService.notifyUserAboutDataExportCreation(dataExport);
        }
        catch (ArtemisMailException e) {
            log.warn("Failed to send email about successful data export creation");
        }
        return true;
    }

    /**
     * Handles the case of a failed data export creation.
     * <p>
     * This includes setting the state of the data export to failed, notifying the user about the failure and sending an email to the admin with the exception why the export
     * failed.
     *
     * @param dataExport the data export that failed to be created
     * @param exception  the exception that occurred during the creation
     */
    private void handleCreationFailure(DataExport dataExport, Exception exception) {
        dataExport.setDataExportState(DataExportState.FAILED);
        dataExport = dataExportRepository.save(dataExport);
        singleUserNotificationService.notifyUserAboutDataExportFailure(dataExport);
        Optional<User> admin = userService.findInternalAdminUser();
        if (admin.isEmpty()) {
            log.warn("No internal admin user found. Cannot send email to admin about data export failure.");
            return;
        }
        mailService.sendDataExportFailedEmailToAdmin(admin.get(), dataExport, exception);
    }

    /**
     * Finishes the creation of the data export by setting the file path to the zip file, the state to EMAIL_SENT and the creation finished date.
     *
     * @param dataExport     the data export whose creation is finished
     * @param dataExportPath the path to the zip file containing the data export
     * @return the updated data export from the database
     */
    private DataExport finishDataExportCreation(DataExport dataExport, Path dataExportPath) {
        dataExport.setFilePath(dataExportPath.toString());
        dataExport.setCreationFinishedDate(ZonedDateTime.now());
        dataExport = dataExportRepository.save(dataExport);
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        return dataExportRepository.save(dataExport);
    }

    /**
     * Prepares the data export by creating the working directory, scheduling it for deletion and setting the state to IN_CREATION.
     * <p>
     * If the path where the data exports are stored does not exist yet, it will be created.
     *
     * @param dataExport the data export to be prepared
     * @return the path to the working directory
     * @throws IOException if the working directory could not be created
     */
    private Path prepareDataExport(DataExport dataExport) throws IOException {
        if (!Files.exists(dataExportsPath)) {
            Files.createDirectories(dataExportsPath);
        }
        dataExport = dataExportRepository.save(dataExport);
        Path workingDirectory = Files.createTempDirectory(dataExportsPath, "data-export-working-dir");
        fileService.scheduleDirectoryPathForRecursiveDeletion(workingDirectory, 30);
        dataExport.setDataExportState(DataExportState.IN_CREATION);
        dataExportRepository.save(dataExport);
        return workingDirectory;
    }

    /**
     * Adds the general user information to the data export.
     * <p>
     * This includes the login, name, email, and registration number (matriculation number).
     *
     * @param user             the user for which the information should be added
     * @param workingDirectory the directory in which the information should be stored
     */
    private void addGeneralUserInformation(User user, Path workingDirectory) throws IOException {
        String[] headers = { "login", "name", "email", "registration number" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();

        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("general_user_information" + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(user.getLogin(), user.getName(), user.getEmail(), user.getRegistrationNumber());
            printer.flush();
        }
    }

    /**
     * Creates the zip file containing the data export.
     *
     * @param userLogin        the login of the user for which the data export was created
     * @param workingDirectory the directory containing the data export
     * @return the path to the zip file
     * @throws IOException if the zip file could not be created
     */
    private Path createDataExportZipFile(String userLogin, Path workingDirectory) throws IOException {
        // There should actually never exist more than one data export for a user at a time (once the feature is fully implemented), but to be sure the name is unique, we add the
        // current timestamp
        return zipFileService.createZipFileWithFolderContent(dataExportsPath.resolve("data-export_" + userLogin + ZonedDateTime.now().toEpochSecond() + ZIP_FILE_EXTENSION),
                workingDirectory, null);
    }
}
