package de.tum.in.www1.artemis.security.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
     * @return the annotation if it is present, empty otherwise
     */
    public static <T extends Annotation> Optional<T> getAnnotation(Class<T> clazz, ProceedingJoinPoint joinPoint) {
        final var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        T annotation = method.getAnnotation(clazz);
        if (annotation == null) {
            for (Annotation a : method.getDeclaredAnnotations()) {
                annotation = a.annotationType().getAnnotation(clazz);
                if (annotation != null) {
                    break;
                }
            }
        }
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(clazz);
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

    /**
     * Extracts the value from the annotation
     *
     * @param annotation the annotation
     * @param valueName  the value name
     * @return the value if it is present, otherwise an exception is thrown
     */
    public static <T extends Annotation, V> Optional<V> getValue(T annotation, String valueName, Class<V> valueType) {
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
