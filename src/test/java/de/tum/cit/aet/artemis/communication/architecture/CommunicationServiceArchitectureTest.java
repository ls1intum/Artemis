package de.tum.cit.aet.artemis.communication.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.jpa.domain.DeleteSpecification;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.data.jpa.domain.UpdateSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.parser.PartTree;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.service.AnswerMessageService;
import de.tum.cit.aet.artemis.communication.service.ConversationMessagingService;
import de.tum.cit.aet.artemis.communication.service.PostingService;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleServiceArchitectureTest;

class CommunicationServiceArchitectureTest extends AbstractModuleServiceArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".communication";
    }

    @Test
    void shouldCheckConversationCourseWhenWritingMessages() {
        methodsOfThisModuleThat().areDeclaredInClassesThat(belongToAnyOf(ConversationMessagingService.class, AnswerMessageService.class))
                .should(new ArchCondition<>("call the shared conversation course check when writing messages") {

                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        var writes = new ArrayList<JavaMethodCall>();
                        for (var call : method.getMethodCallsFromSelf()) {
                            if (writesPosting(call, events)) {
                                writes.add(call);
                            }
                        }
                        if (!writes.isEmpty() && method.getMethodCallsFromSelf().stream().noneMatch(CommunicationServiceArchitectureTest::callsConversationCourseCheck)) {
                            events.add(violated(method, "%s writes a message through %s without calling the shared conversation course check".formatted(method.getFullName(),
                                    writes.stream().map(call -> call.getTarget().getFullName()).toList())));
                        }
                    }
                }).check(productionClasses);
    }

    @Test
    void shouldRecognizeRepresentativePostingWrites() {
        for (String name : new String[] { "createMessage", "verifyAnswerMessage" }) {
            methodsOfThisModuleThat().areDeclaredInClassesThat(belongToAnyOf(ConversationMessagingService.class, AnswerMessageService.class)).and().haveName(name)
                    .should(new ArchCondition<>("write a posting in a repository") {

                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            if (method.getMethodCallsFromSelf().stream().noneMatch(call -> writesPosting(call, events))) {
                                events.add(violated(method, method.getFullName() + " was not recognized as a posting write"));
                            }
                        }
                    }).check(productionClasses);
        }
    }

    @Test
    void shouldClassifySpringDataPostingWrites() throws NoSuchMethodException {
        assertThat(ResolvableType.forClass(PostingRepositoryFixture.class).as(Repository.class).resolveGeneric(0)).isEqualTo(Post.class);
        assertThat(directlyWrites(JpaRepository.class.getMethod("save", Object.class), PostRepository.class, Post.class)).isTrue();
        assertThat(directlyWrites(JpaRepository.class.getMethod("saveAndFlush", Object.class), PostRepository.class, Post.class)).isTrue();
        assertThat(directlyWrites(JpaRepository.class.getMethod("deleteAllInBatch"), PostRepository.class, Post.class)).isTrue();
        assertThat(directlyWrites(JpaRepository.class.getMethod("deleteInBatch", Iterable.class), PostRepository.class, Post.class)).isTrue();
        assertThat(directlyWrites(JpaRepository.class.getMethod("flush"), PostRepository.class, Post.class)).isTrue();
        assertThat(directlyWrites(JpaSpecificationExecutor.class.getMethod("update", UpdateSpecification.class), PostRepository.class, Post.class)).isTrue();
        assertThat(directlyWrites(JpaSpecificationExecutor.class.getMethod("delete", DeleteSpecification.class), PostRepository.class, Post.class)).isTrue();
        assertThat(directlyWrites(JpaSpecificationExecutor.class.getMethod("delete", PredicateSpecification.class), PostRepository.class, Post.class)).isTrue();
        assertThat(directlyWrites(AnswerPostRepository.class.getMethod("updateVerificationIfUnverified", long.class, User.class, ZonedDateTime.class), AnswerPostRepository.class,
                AnswerPost.class)).isTrue();
        assertThat(directlyWrites(PostingRepositoryFixture.class.getMethod("deleteByContent", String.class), PostingRepositoryFixture.class, Post.class)).isTrue();
        assertThat(directlyWrites(PostingRepositoryFixture.class.getMethod("runProcedure"), PostingRepositoryFixture.class, Post.class)).isTrue();
        assertThat(directlyWrites(PostingRepositoryFixture.class.getMethod("deletePreview"), PostingRepositoryFixture.class, Post.class)).isFalse();
        assertThat(directlyWrites(JpaRepository.class.getMethod("findById", Object.class), PostRepository.class, Post.class)).isFalse();
    }

    private interface GenericRepositoryFixture<T> extends ArtemisJpaRepository<T, Long> {
    }

    private interface PostingRepositoryFixture extends GenericRepositoryFixture<Post> {

        void deleteByContent(String content);

        @Procedure("update_posting")
        void runProcedure();

        void deletePreview();
    }

    private static boolean callsConversationCourseCheck(JavaMethodCall call) {
        if (!call.getTarget().getName().equals("ensureConversationBelongsToCourseElseThrow")) {
            return false;
        }
        return call.getTarget().resolveMember().filter(JavaMethod.class::isInstance).map(JavaMethod.class::cast).map(JavaMethod::reflect).map(
                method -> method.getDeclaringClass().equals(PostingService.class) && Arrays.equals(method.getParameterTypes(), new Class<?>[] { Conversation.class, Long.class }))
                .orElse(false);
    }

    private static boolean writesPosting(JavaMethodCall call, ConditionEvents events) {
        Class<?> repositoryType = call.getTargetOwner().reflect();
        if (!Repository.class.isAssignableFrom(repositoryType)) {
            return false;
        }
        Class<?> domain = ResolvableType.forClass(repositoryType).as(Repository.class).resolveGeneric(0);
        if (domain == null) {
            events.add(violated(call, "Cannot resolve repository domain of " + repositoryType.getName() + " at " + call.getSourceCodeLocation()));
            return false;
        }
        if (domain != Post.class && domain != AnswerPost.class) {
            return false;
        }
        var resolved = call.getTarget().resolveMember();
        if (resolved.isEmpty() || !(resolved.get() instanceof JavaMethod target)) {
            events.add(violated(call, "Cannot resolve posting repository call " + call.getTarget().getFullName() + " at " + call.getSourceCodeLocation()));
            return false;
        }
        if (directlyWrites(target.reflect(), repositoryType, domain)) {
            return true;
        }
        return target.reflect().isDefault() && target.getMethodCallsFromSelf().stream().anyMatch(inner -> wrapperWrites(inner, repositoryType, domain, events));
    }

    private static boolean wrapperWrites(JavaMethodCall call, Class<?> repositoryType, Class<?> domain, ConditionEvents events) {
        if (!Repository.class.isAssignableFrom(call.getTargetOwner().reflect())) {
            return false;
        }
        var resolved = call.getTarget().resolveMember();
        if (resolved.isEmpty() || !(resolved.get() instanceof JavaMethod target)) {
            events.add(violated(call, "Cannot resolve repository call in default wrapper: " + call.getTarget().getFullName()));
            return false;
        }
        return directlyWrites(target.reflect(), repositoryType, domain);
    }

    private static boolean directlyWrites(Method method, Class<?> repositoryType, Class<?> domain) {
        if (AnnotatedElementUtils.hasAnnotation(method, Modifying.class) || AnnotatedElementUtils.hasAnnotation(method, Procedure.class)) {
            return true;
        }
        if (Arrays.stream(JpaRepository.class.getMethods()).anyMatch(base -> isFrameworkWrite(base, false) && matchesContract(method, base, repositoryType))) {
            return true;
        }
        if (Arrays.stream(JpaSpecificationExecutor.class.getMethods()).anyMatch(base -> isFrameworkWrite(base, true) && matchesContract(method, base, repositoryType))) {
            return true;
        }
        return isDerivedDelete(method, repositoryType, domain);
    }

    private static boolean isFrameworkWrite(Method method, boolean specificationExecutor) {
        String name = method.getName();
        return specificationExecutor ? name.equals("update") || name.equals("delete") : name.startsWith("save") || name.startsWith("delete") || name.equals("flush");
    }

    private static boolean matchesContract(Method method, Method base, Class<?> repositoryType) {
        if (!method.getName().equals(base.getName()) || method.getParameterCount() != base.getParameterCount()) {
            return false;
        }
        for (int index = 0; index < base.getParameterCount(); index++) {
            Class<?> expected = base.getParameterTypes()[index];
            Class<?> specialized = ResolvableType.forMethodParameter(base, index, repositoryType).resolve();
            Class<?> actual = method.getParameterTypes()[index];
            if (actual != expected && actual != specialized) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDerivedDelete(Method method, Class<?> repositoryType, Class<?> domain) {
        if (method.getDeclaringClass() != repositoryType || !Modifier.isAbstract(method.getModifiers()) || method.isAnnotationPresent(Query.class)
                || method.isAnnotationPresent(NativeQuery.class) || method.isAnnotationPresent(Procedure.class)) {
            return false;
        }
        if (!method.getName().matches("^(delete|remove).*By.+")) {
            return false;
        }
        // A direct abstract declaration is a conservative candidate: named queries or custom implementations may override Spring's derived lookup.
        return new PartTree(method.getName(), domain).isDelete();
    }
}
