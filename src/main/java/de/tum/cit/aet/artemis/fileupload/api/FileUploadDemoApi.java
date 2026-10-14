package de.tum.cit.aet.artemis.fileupload.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.fileupload.config.FileUploadEnabled;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadDemoService;

/**
 * Lets the demo seeding create the demo content of the file upload exercise module. Only delegates to {@link FileUploadDemoService}, which holds the seeding logic.
 */
@Conditional(FileUploadEnabled.class)
@Controller
@Lazy
public class FileUploadDemoApi extends AbstractFileModuleApi {

    private final FileUploadDemoService fileUploadDemoService;

    public FileUploadDemoApi(FileUploadDemoService fileUploadDemoService) {
        this.fileUploadDemoService = fileUploadDemoService;
    }

    /**
     * Creates the demo file upload exercise in the given course if it does not exist yet, see {@link FileUploadDemoService#createDemo(Course)}.
     *
     * @param course the demo course the exercise belongs to.
     * @return the demo exercise, whether it already existed or was created by this call.
     */
    public FileUploadExercise createDemo(Course course) {
        return fileUploadDemoService.createDemo(course);
    }
}
