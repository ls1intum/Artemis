import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Generates placeholder revisions and writes them to the project file.
 * Executing this task means that you are overwriting how notifications are sent to the native clients.
 */
public abstract class GenerateSignatureTask extends DefaultTask {

    @TaskAction
    public void performAction() {
        var compileTask = getProject().getTasks().named("compileJava", JavaCompile.class).get();

        try {
            GeneratePlaceholderSignatures.generateSignatures(compileTask.getDestinationDirectory().get().getAsFile(), getProject().getProjectDir());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
