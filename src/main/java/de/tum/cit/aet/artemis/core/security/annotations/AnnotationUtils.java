package de.tum.cit.aet.artemis.core.security.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * Utility class for annotations
 */
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
    @NotNull
    public static <T extends Annotation> Optional<T> getAnnotation(@NotNull Class<T> clazz, @NotNull ProceedingJoinPoint joinPoint) {
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
     * Extracts the value from the annotation
     *
     * @param annotation the annotation
     * @param valueName  the value name
     * @param valueType  the value type
     * @param <T>        the type of the annotation
     * @param <V>        the type of the value
     * @return the value if it is present, otherwise an exception is thrown
     */
    @NotNull
    public static <T extends Annotation, V> Optional<V> getValue(@NotNull T annotation, @NotBlank String valueName, @NotNull Class<V> valueType) {
        try {
            Method method = annotation.annotationType().getMethod(valueName);
            Object value = method.invoke(annotation);
            if (method.getReturnType().equals(valueType)) {
                return Optional.ofNullable(valueType.cast(value));
            }
            return Optional.empty();
        }
        catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Extracts the id from the method arguments
     *
     * @param joinPoint the join point
     * @param fieldName the fieldName
     * @return the id if it is present, empty otherwise
     */
    @NotNull
    public static Optional<Long> getIdFromSignature(@NotNull ProceedingJoinPoint joinPoint, @NotBlank String fieldName) {
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
