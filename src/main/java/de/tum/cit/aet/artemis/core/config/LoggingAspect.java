package de.tum.cit.aet.artemis.core.config;

import java.util.Arrays;
import java.util.Objects;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.service.vcs.AbstractVersionControlService;

/**
 * Aspect for logging execution of service and repository Spring components.
 * <p>
 * By default, it only runs with the "dev" profile.
 */
@Aspect
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    private final Environment env;

    public LoggingAspect(Environment env) {
        this.env = env;
    }

    /**
     * Pointcut that matches all repositories, services and Web REST endpoints.
     */
    @Pointcut("within(@org.springframework.stereotype.Repository *)" + " || within(@org.springframework.stereotype.Service *)"
            + " || within(@org.springframework.web.bind.annotation.RestController *)")
    public void springBeanPointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Pointcut that matches all Spring beans in the application's main packages.
     */
    // ToDo: Verify that wildcards work as expected
    @Pointcut("within(de.tum.cit.aet.artemis..*.repository..*)" + " || within(de.tum.cit.aet.artemis..*.service..*)" + " || within(de.tum.cit.aet.artemis..*.web..*)")
    public void applicationPackagePointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Pointcut excluding the services whose arguments or return values are live credentials.
     * <p>
     * {@link #logAround} prints every argument and every return value, so a service that takes or produces a secret
     * would write it into the log of every node running the development profile, from where it spreads to log
     * aggregation and support bundles. The token is deliberately masked in {@code BuildJobQueueItem.toString()} for the
     * same reason; these exclusions close the other ways out, and both are needed:
     * <ul>
     * <li>{@code BuildJobCloneTokenService} mints a build job's clone token and receives the presented one to compare
     * against, so it appears as both a return value and an argument.</li>
     * <li>{@code BuildJobGitService} takes the token as the argument of {@code setCloneTokenForCurrentThread}, which
     * runs once per build job on any node carrying both the core and buildagent profiles - the standard single node
     * development setup, which is exactly where this aspect is active.</li>
     * </ul>
     */
    @Pointcut("!within(de.tum.cit.aet.artemis.localci.service.BuildJobCloneTokenService) && !within(de.tum.cit.aet.artemis.buildagent.service.BuildJobGitService)")
    public void notACredentialHandlingBean() {
        // Method is empty as this is just a Pointcut, the implementations are in the advices.
    }

    /**
     * Advice that logs methods throwing exceptions.
     *
     * @param joinPoint join point for advice.
     * @param e         exception.
     */
    @AfterThrowing(pointcut = "applicationPackagePointcut() && springBeanPointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        if (AbstractVersionControlService.isReadFullyShortReadOfBlockException(e)) {
            // ignore
            return;
        }

        if (e instanceof LocalVCAuthException) {
            if (Objects.equals(e.getMessage(), "No authorization header provided")) {
                // ignore, this is a common case and does not need to be logged
                return;
            }
            else if (e.getMessage() != null && e.getMessage().startsWith("The username has to be")) {
                // ignore, this is a common case and does not need to be logged
                return;
            }
        }

        if (env.acceptsProfiles(Profiles.of(ArtemisConstants.SPRING_PROFILE_DEVELOPMENT))) {
            log.error("Exception in {}.{}() with cause = '{}' and exception = '{}'", joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                    e.getCause() != null ? e.getCause() : "NULL", e.getMessage(), e);
        }
        else {
            log.error("Exception in {}.{}() with cause = {}", joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                    e.getCause() != null ? e.getCause() : "NULL");
        }
    }

    /**
     * Advice that logs when a method is entered and exited.
     *
     * @param joinPoint join point for advice.
     * @return result.
     * @throws Throwable throws {@link IllegalArgumentException}.
     */
    @Around("applicationPackagePointcut() && springBeanPointcut() && notACredentialHandlingBean()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        if (log.isDebugEnabled()) {
            log.debug("Enter: {}.{}() with argument[s] = {}", joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                    Arrays.toString(joinPoint.getArgs()));
        }
        try {
            Object result = joinPoint.proceed();
            if (log.isDebugEnabled()) {
                log.debug("Exit: {}.{}() with result = {}", joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(), result);
            }
            return result;
        }
        catch (IllegalArgumentException e) {
            log.error("Illegal argument: {} in {}.{}()", Arrays.toString(joinPoint.getArgs()), joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName());
            throw e;
        }
    }
}
