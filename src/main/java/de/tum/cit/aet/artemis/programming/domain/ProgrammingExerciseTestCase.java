package de.tum.cit.aet.artemis.programming.domain;

import static de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType.DEFAULT;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * A ProgrammingExerciseTestCase.
 */
@Entity
@Table(name = "programming_exercise_test_case")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseTestCase extends DomainObject {

    private static final int MAX_TEST_NAME_LENGTH = 255;

    private static final int MAX_TEST_CLASS_LENGTH = 255;

    @Column(name = "test_name")
    private String testName;

    /**
     * Binary name of the test class that declares this test, as reported by the solution build (e.g. {@code de.tum.Foo$Bar} for a nested class).
     * {@code null} for test cases registered before the class was tracked, or when the build did not report it.
     */
    @Column(name = "test_class", length = MAX_TEST_CLASS_LENGTH)
    private String testClass;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "active", columnDefinition = "boolean DEFAULT false")
    private Boolean active = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    private Visibility visibility;

    @Column(name = "bonus_multiplier")
    private Double bonusMultiplier;

    @Column(name = "bonus_points")
    private Double bonusPoints;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "programming_exercise_task_test_case", joinColumns = @JoinColumn(name = "test_case_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "task_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "testCases", "exercise" })
    private Set<ProgrammingExerciseTask> tasks = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("testCases")
    @JoinColumn(nullable = false)
    @Parent
    private ProgrammingExercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_case_type", nullable = false)
    private ProgrammingExerciseTestCaseType type = DEFAULT;     // default value

    public ProgrammingExerciseTestCase id(Long id) {
        setId(id);
        return this;
    }

    public String getTestName() {
        return testName;
    }

    /**
     * Keeps a CI test name within the database limit without merging distinct names that share a prefix.
     * The digest follows the same codepoint case equivalence as {@link String#equalsIgnoreCase(String)}, so
     * changing only case retains the test case and its grading settings. The readable prefix stays unchanged.
     * A parameter list cut by the length limit is closed after the digest so task and diagram references still parse.
     *
     * @param testName the original name reported by CI or written in a task marker
     * @return the name used to store and look up the test case
     */
    public static String normalizeTestName(String testName) {
        if (testName.length() <= MAX_TEST_NAME_LENGTH) {
            return testName;
        }
        StringBuilder foldedName = new StringBuilder(testName.length());
        testName.codePoints().forEach(codePoint -> foldedName.appendCodePoint(Character.toLowerCase(Character.toUpperCase(codePoint))));
        String suffix = "-" + sha256Hex(foldedName.toString());
        int prefixLength = MAX_TEST_NAME_LENGTH - suffix.length();
        int parameterStart = testName.lastIndexOf('(', prefixLength - 1);
        boolean truncatedParameters = parameterStart > testName.lastIndexOf(')', prefixLength - 1);
        if (truncatedParameters) {
            prefixLength--;
        }
        if (Character.isHighSurrogate(testName.charAt(prefixLength - 1)) && Character.isLowSurrogate(testName.charAt(prefixLength))) {
            prefixLength--;
        }
        // Reserving the closing parenthesis can remove an opener exactly at the prefix boundary.
        return testName.substring(0, prefixLength) + suffix + (truncatedParameters && parameterStart < prefixLength ? ")" : "");
    }

    @JsonIgnore
    public String getTestClass() {
        return testClass;
    }

    public void setTestClass(String testClass) {
        this.testClass = normalizeTestClass(testClass);
    }

    /**
     * Brings a reported test class name into the form that is stored: blank, missing or too long names carry no usable information and become {@code null}.
     *
     * @param testClass the class name reported by CI, may be null
     * @return the class name to store, or null
     */
    public static String normalizeTestClass(String testClass) {
        if (testClass == null || testClass.isBlank() || testClass.length() > MAX_TEST_CLASS_LENGTH) {
            return null;
        }
        return testClass;
    }

    /**
     * Checks whether this test is declared in the given class or in a class nested inside it. A failing {@code @BeforeAll} of a class keeps the tests of its nested classes from
     * running as well, whereas a failure in a nested class leaves the tests of the outer class alone.
     *
     * @param failedClass the normalized binary name of the class that failed, e.g. {@code de.tum.Foo}
     * @return true if the class of this test is known and equals {@code failedClass} or starts with {@code failedClass$}
     */
    public boolean isDeclaredInOrBelow(String failedClass) {
        return isClassInOrBelow(testClass, failedClass);
    }

    /**
     * Checks whether a class is the given class or nested inside it, going by binary names ({@code Outer$Inner}).
     *
     * @param testClass   the class to check, may be null
     * @param failedClass the enclosing class, may be null
     * @return true if both are known and {@code testClass} equals {@code failedClass} or starts with {@code failedClass$}
     */
    public static boolean isClassInOrBelow(String testClass, String failedClass) {
        return testClass != null && failedClass != null && (testClass.equals(failedClass) || testClass.startsWith(failedClass + "$"));
    }

    public ProgrammingExerciseTestCase testName(String testName) {
        this.testName = testName;
        return this;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public Double getWeight() {
        return weight;
    }

    public ProgrammingExerciseTestCase weight(Double weight) {
        this.weight = weight;
        return this;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    @NonNull
    public Double getBonusMultiplier() {
        return bonusMultiplier != null ? bonusMultiplier : 1.0;
    }

    public ProgrammingExerciseTestCase bonusMultiplier(Double bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
        return this;
    }

    public void setBonusMultiplier(Double bonusMultiplier) {
        this.bonusMultiplier = bonusMultiplier;
    }

    @NonNull
    public Double getBonusPoints() {
        return bonusPoints != null ? bonusPoints : 0.0;
    }

    public ProgrammingExerciseTestCase bonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
        return this;
    }

    public void setBonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public Set<ProgrammingExerciseTask> getTasks() {
        return tasks;
    }

    public void setTasks(Set<ProgrammingExerciseTask> tasks) {
        this.tasks = tasks;
    }

    public Boolean isActive() {
        return active;
    }

    public ProgrammingExerciseTestCase active(Boolean active) {
        this.active = active;
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public ProgrammingExerciseTestCase exercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    @JsonIgnore
    public boolean isAfterDueDate() {
        return visibility == Visibility.AFTER_DUE_DATE;
    }

    @JsonIgnore
    public boolean isInvisible() {
        return visibility == Visibility.NEVER;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public ProgrammingExerciseTestCase visibility(Visibility visibility) {
        this.visibility = visibility;
        return this;
    }

    public ProgrammingExerciseTestCaseType getType() {
        return type;
    }

    public void setType(ProgrammingExerciseTestCaseType programmingExerciseTestCaseType) {
        this.type = programmingExerciseTestCaseType;
    }

    /**
     * Needs to be checked and updated if there is a new class attribute. Creates a clone with all attributes set to the value of the object, including the id.
     *
     * @return a clone of the object.
     */
    @Override
    public ProgrammingExerciseTestCase clone() {
        ProgrammingExerciseTestCase clone = new ProgrammingExerciseTestCase().testName(this.getTestName()).weight(this.getWeight()).active(this.isActive())
                .bonusPoints(this.getBonusPoints()).bonusMultiplier(this.getBonusMultiplier()).visibility(visibility).exercise(this.exercise);
        clone.setTestClass(this.testClass);
        clone.setId(this.getId());
        return clone;
    }

    /**
     * Checks for logical equality based on the name and the exercise
     *
     * @param testCase another test case which should be checked for being the same
     * @return whether this and the other test case are the same based on name and exercise
     */
    public boolean isSameTestCase(ProgrammingExerciseTestCase testCase) {
        return testCase.getTestName().equalsIgnoreCase(this.getTestName()) && this.getExercise().getId().equals(testCase.getExercise().getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExerciseTestCase{" + "id=" + getId() + ", testName='" + testName + '\'' + ", weight=" + weight + ", active=" + active + ", visibility=" + visibility
                + ", bonusMultiplier=" + bonusMultiplier + ", bonusPoints=" + bonusPoints + ", type=" + type + '}';
    }

    /**
     * Check if the provided test was found in the result's feedbacks with positive = true.
     *
     * @param result of the build run.
     * @return true if there is a positive feedback for a given test.
     */
    public boolean isSuccessful(Result result) {
        return result.getTestCaseFeedbacks().stream().anyMatch(feedback -> this.equals(feedback.getTestCase()) && Boolean.TRUE.equals(feedback.isPositive()));
    }

    /**
     * Check if the provided test was not found in the result's feedbacks.
     *
     * @param result of the build run.
     * @return true if there is no feedback for a given test.
     */
    public boolean wasNotExecuted(Result result) {
        return result.getTestCaseFeedbacks().stream().noneMatch(feedback -> this.equals(feedback.getTestCase()));
    }
}
