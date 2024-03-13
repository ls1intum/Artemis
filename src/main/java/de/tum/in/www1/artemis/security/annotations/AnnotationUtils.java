package de.tum.in.www1.artemis.security.annotations;

import java.lang.annotation.Annotation;
import java.util.Arrays;
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
     * @param <T>       the type of the annotation
     * @return the annotation if it is present, empty otherwise
     */
    public static <T extends Annotation> Optional<T> getAnnotation(Class<T> clazz, ProceedingJoinPoint joinPoint) {
        final var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        T annotation = method.getAnnotation(clazz);
        if (annotation != null) {
            return Optional.of(annotation);
        }
        for (Annotation a : method.getDeclaredAnnotations()) {
            annotation = a.annotationType().getAnnotation(clazz);
            if (annotation != null) {
                return Optional.of(annotation);
            }
        }
        annotation = method.getDeclaringClass().getAnnotation(clazz);
        if (annotation != null) {
            return Optional.of(annotation);
        }
        for (Annotation a : method.getDeclaringClass().getDeclaredAnnotations()) {
            annotation = a.annotationType().getAnnotation(clazz);
            if (annotation != null) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    /**
     * Extracts the id from the method arguments
     *
     * @param joinPoint the join point
     * @param fieldName the fieldName
     * @return the id if it is present, empty otherwise
     */
    public static Optional<Long> getIdFromSignature(ProceedingJoinPoint joinPoint, String fieldName) {
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        final int indexOfId = Arrays.asList(signature.getParameterNames()).indexOf(fieldName);
        Object[] args = joinPoint.getArgs();

        if (indexOfId < 0 || args.length <= indexOfId) {
            return Optional.empty();
        }

        if (args[indexOfId] instanceof Long id) {
            return Optional.of(id);
        }
        return Optional.empty();
    }
}
