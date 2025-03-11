
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public class NonDtoEndpointFinder {

    // Define the package where your controllers reside.
    private static final String CONTROLLER_PACKAGE = "de.tum.cit.aet.artemis";

    // Base directory of compiled classes (e.g. Maven default: target/classes)
    private static final String BASE_CLASSES_DIR = "C:\\private\\Artemis\\build\\classes\\java\\main";

    // Fully qualified name of the RestController annotation
    private static final String REST_CONTROLLER_ANNOTATION = "org.springframework.web.bind.annotation.RestController";

    public static void main(String[] args) {
        // Convert package name to a relative directory path.
        String packagePath = CONTROLLER_PACKAGE.replace('.', File.separatorChar);
        File packageDir = new File(BASE_CLASSES_DIR, packagePath);

        if (!packageDir.exists() || !packageDir.isDirectory()) {
            System.err.println("Package directory not found: " + packageDir.getAbsolutePath());
            return;
        }

        List<String> classNames = new ArrayList<>();
        listClassFiles(packageDir, CONTROLLER_PACKAGE, classNames);

        int nonDtoEndpointCount = 0;
        for (String className : classNames) {
            try {
                System.out.println("Checking class: " + className);
                Class<?> clazz = Class.forName(className);
                // Check if the class is annotated with @RestController by name.
                if (!hasAnnotation(clazz, REST_CONTROLLER_ANNOTATION)) {
                    continue;
                }
                for (Method method : clazz.getDeclaredMethods()) {
                    // Only consider public methods.
                    if (!Modifier.isPublic(method.getModifiers())) {
                        continue;
                    }
                    boolean usesDto = false;
                    // Check all parameters for types ending with "DTO".
                    for (Parameter parameter : method.getParameters()) {
                        if (parameter.getType().getSimpleName().endsWith("DTO")) {
                            usesDto = true;
                            break;
                        }
                    }
                    // Check the return type (if not void).
                    if (!usesDto && !method.getReturnType().equals(Void.TYPE)) {
                        if (method.getReturnType().getSimpleName().endsWith("DTO")) {
                            usesDto = true;
                        }
                    }
                    if (!usesDto) {
                        nonDtoEndpointCount++;
                        System.out.println("Endpoint without DTO in " + clazz.getName() + ": " + method);
                    }
                }
            }
            catch (ClassNotFoundException e) {
                System.err.println("Class not found: " + className);
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }
        System.out.println("Total endpoints not using DTOs: " + nonDtoEndpointCount);
    }

    /**
     * Checks whether the given class has an annotation with the specified fully qualified name.
     */
    private static boolean hasAnnotation(Class<?> clazz, String annotationFQN) {
        return java.util.Arrays.stream(clazz.getAnnotations()).anyMatch(ann -> ann.annotationType().getName().equals(annotationFQN));
    }

    /**
     * Recursively scans the given directory for .class files and adds fully qualified class names to the list.
     */
    private static void listClassFiles(File directory, String packageName, List<String> classNames) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                listClassFiles(file, packageName + "." + file.getName(), classNames);
            }
            else if (file.getName().endsWith(".class")) {
                String simpleName = file.getName().substring(0, file.getName().length() - 6);
                classNames.add(packageName + "." + simpleName);
            }
        }
    }
}
