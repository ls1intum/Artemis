package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import de.tum.cit.aet.artemis.core.config.LoggingAspect;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;

/**
 * The beans of Metis carry the students' own code and their answers, as changed files, inside the prompts of the language model, as answers to rate or as
 * questions and answers they return, so none of them is logged with its arguments and results by {@link LoggingAspect}, which writes every argument and result
 * at debug level. Their failures are logged with the method and the type of the exception only, since its message or cause can quote the same content. The
 * pointcuts of the advices are evaluated as Spring evaluates them.
 */
class MetisCodeLoggingTest {

    @Test
    void noBeanOfTheModuleIsLoggedWithItsArgumentsAndResults() throws NoSuchMethodException {
        AspectJExpressionPointcut pointcut = pointcut(LoggingAspect.class.getMethod("logAround", ProceedingJoinPoint.class).getAnnotation(Around.class).value());
        List<Class<?>> beans = beansOfTheModule();

        // Among them the diff, the prompts and the language model, which carry the code, and the student check and the rating, which carry the answers
        assertThat(beans).extracting(Class::getSimpleName).contains("MetisDiffService", "MetisPromptTemplateService", "MetisLlmService", "MetisStudentCheckService",
                "MetisAnswerRatingService", "MetisDashboardService", "MetisQuestionRepository", "MetisStudentCheckResource");
        for (Class<?> type : beans) {
            assertThat(publicMethodsOf(type)).as("public methods of %s", type.getSimpleName()).noneMatch(method -> pointcut.matches(method, type));
        }
    }

    @Test
    void theFailuresOfTheModuleAreLoggedWithoutTheirMessagesOrCauses() throws NoSuchMethodException {
        AspectJExpressionPointcut withMessageAndCause = afterThrowingPointcut("logAfterThrowing");
        AspectJExpressionPointcut withTheTypeOnly = afterThrowingPointcut("logMetisFailure");

        List<Class<?>> beans = beansOfTheModule();

        for (Class<?> type : beans) {
            assertThat(publicMethodsOf(type)).as("public methods of %s", type.getSimpleName()).noneMatch(method -> withMessageAndCause.matches(method, type));
        }
        // The services and the resources still report every failure, with the type of the exception
        for (Class<?> type : beans.stream().filter(type -> !type.isInterface()).toList()) {
            assertThat(publicMethodsOf(type)).as("public methods of %s", type.getSimpleName()).allMatch(method -> withTheTypeOnly.matches(method, type));
        }
    }

    @Test
    void theBeansOfOtherModulesAreStillLogged() throws NoSuchMethodException {
        AspectJExpressionPointcut pointcut = pointcut(LoggingAspect.class.getMethod("logAround", ProceedingJoinPoint.class).getAnnotation(Around.class).value());
        AspectJExpressionPointcut failures = afterThrowingPointcut("logAfterThrowing");
        AspectJExpressionPointcut metisFailures = afterThrowingPointcut("logMetisFailure");

        assertThat(publicMethodsOf(ResourceLoaderService.class)).as("the pointcuts are evaluated and still cover the rest of the application")
                .anyMatch(method -> pointcut.matches(method, ResourceLoaderService.class)).anyMatch(method -> failures.matches(method, ResourceLoaderService.class))
                .noneMatch(method -> metisFailures.matches(method, ResourceLoaderService.class));
    }

    private static List<Class<?>> beansOfTheModule() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("de.tum.cit.aet.artemis.metis").stream()
                .filter(type -> type.isAnnotatedWith(Service.class) || type.isAnnotatedWith(Repository.class) || type.isAnnotatedWith(RestController.class)).map(JavaClass::reflect)
                .toList();
    }

    private static AspectJExpressionPointcut afterThrowingPointcut(String advice) throws NoSuchMethodException {
        return pointcut(LoggingAspect.class.getMethod(advice, JoinPoint.class, Throwable.class).getAnnotation(AfterThrowing.class).pointcut());
    }

    private static AspectJExpressionPointcut pointcut(String expression) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setPointcutDeclarationScope(LoggingAspect.class);
        pointcut.setExpression(expression);
        return pointcut;
    }

    private static List<Method> publicMethodsOf(Class<?> type) {
        return List.of(type.getDeclaredMethods()).stream().filter(method -> Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())).toList();
    }
}
