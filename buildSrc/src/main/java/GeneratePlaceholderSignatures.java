import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import com.google.gson.Gson;
import io.github.classgraph.ClassGraph;

public class GeneratePlaceholderSignatures {

    private static final String ANNOTATION_NAME = "de.tum.in.www1.artemis.domain.notification.NotificationPlaceholderClass";

    public static final String SIGNATURE_FILE_NAME = "placeholder-signatures.json";

    public static void generateSignatures(File inputClassDir, File outputDir) throws IOException {
        try (var scanResult = new ClassGraph()
            .overrideClasspath(inputClassDir)
            .acceptPackages("de.tum.in.www1.artemis")
            .enableAllInfo()
            .scan()) {
            var classes = scanResult.getClassesWithAnnotation(ANNOTATION_NAME);

            var signatures = classes.stream().map(placeholderClass -> {
                Class<?> loaded = placeholderClass.loadClass();
                var fieldDescriptions = Arrays.stream(loaded.getDeclaredFields()).map(field -> new FieldDescription(field.getName(), field.getType().getName())).toList();

                var notificationType = (String) placeholderClass.getAnnotationInfo(ANNOTATION_NAME).getParameterValues().get("value").getValue();

                return new ClassSignature(notificationType, fieldDescriptions);
            }).toList();

            var signature = new Gson().toJson(signatures);
            outputDir.mkdirs();

            var outputFile = new File(outputDir, SIGNATURE_FILE_NAME);

            try (var writer = new FileWriter(outputFile)) {
                writer.write(signature);
            }
        }
    }

    private record ClassSignature(String notificationType, List<FieldDescription> fieldDescriptions) {
    }

    private record FieldDescription(String fieldName, String fieldType) {
    }
}
