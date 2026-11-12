package de.tum.cit.aet.artemis.core.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceRoleInCourse;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * A user who is not a member of a course is refused by every endpoint that requires a role in that course.
 * <p>
 * The test sends one request to each reading endpoint whose only path variable is the course id and that is guarded by a course role annotation, as a signed-in
 * user without any group of the course. The role annotation is evaluated before the method runs, so the answer must be 403 whatever the course contains. A
 * different answer is a gap: either the annotation does not do what it says, or the endpoint is reachable without the role it declares.
 * <p>
 * Endpoints that need a parameter or a body are answered with a bad request before the annotation is evaluated, and are not covered here. The integration tests of
 * the module cover them with a refused request of their own, as the security guideline asks.
 */
class AuthorizationOutsiderEndpointTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "outsiderendpoints";

    private static final String OUTSIDER = TEST_PREFIX + "outsider";

    /**
     * Endpoints that are meant to answer a user outside the course. Each entry needs a reason; the list is not to grow.
     */
    private static final Set<String> ALLOWED_FOR_OUTSIDERS = Set.of();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        userUtilService.createAndSaveUser(OUTSIDER);
        course = courseUtilService.createCourse();
    }

    @Test
    @WithMockUser(username = OUTSIDER, roles = "USER")
    void courseScopedReadingEndpointsRefuseAUserOutsideTheCourse() throws Exception {
        var mapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<String, Integer> answers = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            String url = urlToCall(entry.getKey(), entry.getValue());
            if (url != null) {
                answers.put(entry.getValue().getBeanType().getSimpleName() + "#" + entry.getValue().getMethod().getName() + " " + url, status(url));
            }
        }

        List<String> notRefused = new ArrayList<>();
        int refused = 0;
        for (var answer : answers.entrySet()) {
            String endpoint = answer.getKey().substring(0, answer.getKey().indexOf(' '));
            if (answer.getValue() == 403) {
                refused++;
            }
            else if (!ALLOWED_FOR_OUTSIDERS.contains(endpoint) && !isNotDecidedByTheRole(answer.getValue())) {
                notRefused.add(answer.getKey() + " answered " + answer.getValue());
            }
        }
        // guards against a test that finds nothing because the endpoints were not recognized
        assertThat(refused).as("the endpoints that refused the outsider, of %d tested", answers.size()).isGreaterThan(50);
        assertThat(notRefused).as("endpoints that did not refuse a user outside the course").isEmpty();
    }

    /**
     * A bad request or a missing resource means that the request was rejected before the role was evaluated, or that the endpoint depends on data that this test does
     * not create. Neither tells whether the role is enforced.
     */
    private static boolean isNotDecidedByTheRole(int status) {
        return status == 400 || status == 404 || status == 405 || status == 415 || status == 501 || status == 503;
    }

    private int status(String url) throws Exception {
        return request.performMvcRequest(MockMvcRequestBuilders.get(url)).andReturn().getResponse().getStatus();
    }

    /**
     * @return the url to call for the endpoint, or null if the endpoint is not covered by this test
     */
    private String urlToCall(RequestMappingInfo info, HandlerMethod handler) {
        Method method = handler.getMethod();
        var methods = info.getMethodsCondition().getMethods();
        if (methods.size() != 1 || !methods.contains(RequestMethod.GET) || info.getPathPatternsCondition() == null) {
            return null;
        }
        if (!handler.getBeanType().getPackageName().startsWith("de.tum.cit.aet.artemis")) {
            return null;
        }
        EnforceRoleInCourse enforce = AnnotatedElementUtils.findMergedAnnotation(method, EnforceRoleInCourse.class);
        if (enforce == null || !"courseId".equals(enforce.resourceIdFieldName())) {
            return null;
        }
        // only the course id may be a path variable, and nothing else may be required
        for (var parameter : method.getParameters()) {
            if (AnnotationUtils.findAnnotation(parameter, RequestBody.class) != null) {
                return null;
            }
            RequestParam requestParam = AnnotationUtils.findAnnotation(parameter, RequestParam.class);
            if (requestParam != null && requestParam.required() && requestParam.defaultValue().equals("\n\t\t\n\t\t\n\n\t\t\t\t\n")) {
                return null;
            }
            PathVariable pathVariable = AnnotationUtils.findAnnotation(parameter, PathVariable.class);
            if (pathVariable != null && !"courseId".equals(pathVariable.value().isEmpty() ? parameter.getName() : pathVariable.value())) {
                return null;
            }
        }
        var patterns = info.getPathPatternsCondition().getPatternValues();
        if (patterns.size() != 1) {
            return null;
        }
        String pattern = patterns.iterator().next();
        if (!pattern.startsWith("/api/") || !pattern.contains("{courseId}") || pattern.replace("{courseId}", "").contains("{") || pattern.contains("*")) {
            return null;
        }
        return pattern.replace("{courseId}", String.valueOf(course.getId()));
    }
}
