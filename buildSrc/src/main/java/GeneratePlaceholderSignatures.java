import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import io.github.classgraph.AnnotationEnumValue;
import org.jetbrains.annotations.NotNull;

import com.google.gson.GsonBuilder;

import io.github.classgraph.ClassGraph;

public class GeneratePlaceholderSignatures {

    /**
     * Java class name of the annotation. Moving or renaming the original source file also means you have to change it here!
     */
    private static final String ANNOTATION_NAME = "de.tum.in.www1.artemis.domain.notification.NotificationPlaceholderClass";

    /**
     * Output name of the signature file.
     */
    public static final String SIGNATURE_FILE_NAME = "placeholder-signatures.json";

    /**
     * Generates the signatures of the placeholder class files.
     *
     * @param inputClassDir where to find the compiled class files
     * @param outputDir     where to write the signature file to
     * @throws IOException thrown if the signature file could not be written
     */
    public static void generateSignatures(File inputClassDir, File outputDir) throws IOException {
        // Use ClassGraph to scan through the compiled class files
        try (var scanResult = new ClassGraph().overrideClasspath(inputClassDir).acceptPackages("de.tum.in.www1.artemis").enableAllInfo().scan()) {
            // Find the classes that are annotated as a notification placeholder file.
            var classes = scanResult.getClassesWithAnnotation(ANNOTATION_NAME);

            // create a signature for each annotated file.
            var signatures = classes.stream().flatMap(placeholderClass -> {
                Class<?> loaded = placeholderClass.loadClass();
                var fieldDescriptions = Arrays.stream(loaded.getDeclaredFields()).map(field -> new FieldDescription(field.getName(), field.getType().getName())).sorted().toList();

                var notificationTypes = (Object[]) placeholderClass.getAnnotationInfo(ANNOTATION_NAME).getParameterValues().get("values").getValue();

                return Arrays.stream(notificationTypes).map(notificationType -> new ClassSignature(((AnnotationEnumValue) notificationType).getValueName(), fieldDescriptions));
            }).sorted().toList();

            // Signature as json
            var signature = new GsonBuilder().setPrettyPrinting().create().toJson(signatures);
            outputDir.mkdirs();

            // Write the signature file
            var outputFile = new File(outputDir, SIGNATURE_FILE_NAME);
            try (var writer = new FileWriter(outputFile)) {
                writer.write(signature);
            }
        }
    }

    private record ClassSignature(String notificationType,
                                  List<FieldDescription> fieldDescriptions) implements Comparable<ClassSignature> {

        @Override
        public int compareTo(@NotNull GeneratePlaceholderSignatures.ClassSignature classSignature) {
            return notificationType.compareTo(classSignature.notificationType);
        }
    }

    private record FieldDescription(String fieldName, String fieldType) implements Comparable<FieldDescription> {

        @Override
        public int compareTo(@NotNull GeneratePlaceholderSignatures.FieldDescription fieldDescription) {
            return fieldName.compareTo(fieldDescription.fieldName);
        }
    }
}
