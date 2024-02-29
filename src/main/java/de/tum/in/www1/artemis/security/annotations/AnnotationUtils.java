package de.tum.in.www1.artemis.security.annotations;

import java.lang.annotation.Annotation;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

public final class AnnotationUtils {

    private AnnotationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts the annotation from the method or type
     *
     * @param clazz     the annotation class
     * @param joinPoint the join point
     * @return the annotation if it is present, empty otherwise
     */
    public static <T extends Annotation> Optional<T> getAnnotation(Class<T> clazz, ProceedingJoinPoint joinPoint) {
        final var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        T annotation = method.getAnnotation(clazz);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(clazz);
        }
        if (annotation == null) {
            for (Annotation a : method.getDeclaredAnnotations()) {
                annotation = a.annotationType().getAnnotation(clazz);
                if (annotation != null) {
                    break;
                }
            }
        }
        if (annotation == null) {
            for (Annotation a : method.getDeclaringClass().getDeclaredAnnotations()) {
                annotation = a.annotationType().getAnnotation(clazz);
                if (annotation != null) {
                    break;
                }
            }
        }
        return Optional.ofNullable(annotation);
    }
}
