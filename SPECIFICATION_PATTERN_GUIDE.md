# Specification Pattern Guide for Course Queries

This guide demonstrates how to use Spring Data JPA Specifications to replace hard-coded SQL queries with composable, access-policy-aligned specifications.

## Overview

The Specification pattern provides:
- **Composability**: Build complex queries by combining simple specifications
- **Policy Alignment**: Specifications mirror access policy definitions directly
- **Type Safety**: Compile-time checking via JPA Criteria API
- **Maintainability**: Single source of truth for access rules
- **Reusability**: Specifications can be mixed and matched across different queries

## Architecture

```
CourseAccessPolicies.java (Policy Definitions)
        ↓ mirrors
CourseSpecs.java (Specification Factories)
        ↓ uses
CourseRepository.java (extends JpaSpecificationExecutor)
        ↓ called by
Service/Resource Layer
```

## Key Files

1. **`CourseSpecs.java`**: Factory class containing static methods that create `Specification<Course>` objects
2. **`CourseRepository.java`**: Now extends `JpaSpecificationExecutor<Course>` to enable specification-based queries
3. **`CourseAccessPolicies.java`**: Access policy definitions (unchanged, but mirrored in specifications)

## Basic Usage

### 1. Simple Specification Query

```java
// Old approach: Hard-coded query
@Query("SELECT c FROM Course c WHERE c.endDate IS NULL OR c.endDate >= :now")
List<Course> findAllNotEnded(@Param("now") ZonedDateTime now);

// New approach: Using specification
default List<Course> findAllNotEnded() {
    var now = ZonedDateTime.now();
    var spec = CourseSpecs.isNotEnded(now);
    return findAll(spec);
}
```

### 2. Combining Multiple Specifications

```java
// Old approach: Complex hard-coded query
@Query("""
    SELECT c FROM Course c
    WHERE (c.startDate <= :now OR c.startDate IS NULL)
      AND (c.endDate >= :now OR c.endDate IS NULL)
      AND c.learningPathsEnabled = true
""")
List<Course> findAllActiveForUserAndLearningPathsEnabled(@Param("now") ZonedDateTime now);

// New approach: Combining specifications
default List<Course> findActiveCourseswithLearningPathsEnabled() {
    var now = ZonedDateTime.now();
    var spec = CourseSpecs.and(
        CourseSpecs.isActive(now),
        CourseSpecs.hasLearningPathsEnabled(),
        CourseSpecs.distinct()
    );
    return findAll(spec);
}
```

### 3. Access Policy Specifications

```java
// Old approach: Hard-coded access checks in query
@Query("""
    SELECT c FROM Course c
    WHERE c.teachingAssistantGroupName IN :userGroups
       OR c.editorGroupName IN :userGroups
       OR c.instructorGroupName IN :userGroups
""")
List<Course> findAllCoursesByManagementGroupNames(@Param("userGroups") List<String> userGroups);

// New approach: Using access policy specification
default List<Course> findCoursesWithStaffAccess(Set<String> userGroups, boolean isAdmin) {
    var spec = CourseSpecs.hasStaffAccess(userGroups, isAdmin);
    return findAll(spec);
}
```

### 4. Optional Filtering with Nullable Specifications

```java
// New approach: Optional title filter
default Page<Course> findCoursesByTitleWithEditorAccess(
    String partialTitle,  // Can be null
    Set<String> userGroups,
    boolean isAdmin,
    Pageable pageable
) {
    var spec = CourseSpecs.and(
        CourseSpecs.hasEditorAccess(userGroups, isAdmin),
        CourseSpecs.titleContains(partialTitle),  // Returns null if partialTitle is null
        CourseSpecs.distinct()
    );
    return findAll(spec, pageable);
}
```

## Available Specifications in CourseSpecs

### Access Policy Specifications

These mirror the access policies defined in `CourseAccessPolicies.java`:

| Specification | Access Policy | Description |
|--------------|---------------|-------------|
| `hasStudentAccess(userGroups, isAdmin)` | `courseStudentAccessPolicy` | Student, TA, editor, instructor, or admin |
| `hasStaffAccess(userGroups, isAdmin)` | `courseStaffAccessPolicy` | TA, editor, instructor, or admin |
| `hasEditorAccess(userGroups, isAdmin)` | `courseEditorAccessPolicy` | Editor, instructor, or admin |
| `hasInstructorAccess(userGroups, isAdmin)` | `courseInstructorAccessPolicy` | Instructor or admin |
| `isVisibleToUser(userGroups, isAdmin, now)` | `courseVisibilityPolicy` | Visibility including start date gating for students |

### Filtering Specifications

| Specification | Description |
|--------------|-------------|
| `titleContains(partialTitle)` | Case-insensitive title search (nullable) |
| `hasSemester(semester)` | Filter by semester (nullable) |
| `excludeTestCourses()` | Exclude test courses |

### Temporal Specifications

| Specification | Description |
|--------------|-------------|
| `isActive(now)` | Course is active (started and not ended) |
| `isNotEnded(now)` | Course has not ended yet |
| `hasActiveEnrollment(now)` | Enrollment window is currently open |

### Feature Flag Specifications

| Specification | Description |
|--------------|-------------|
| `hasLearningPathsEnabled()` | Learning paths are enabled |

### Utility Specifications

| Specification | Description |
|--------------|-------------|
| `distinct()` | Ensure distinct results |

### Helper Methods

| Method | Description |
|--------|-------------|
| `and(spec1, spec2, ...)` | Combine specifications with AND logic, ignoring nulls |
| `or(spec1, spec2, ...)` | Combine specifications with OR logic, ignoring nulls |

## Service Layer Usage Examples

### Example 1: Course Management Service

```java
@Service
public class CourseManagementService {

    private final CourseRepository courseRepository;
    private final UserService userService;

    public List<Course> getCoursesForCurrentUser() {
        User user = userService.getCurrentUser();
        Set<String> userGroups = user.getGroups();
        boolean isAdmin = authCheckService.isAdmin(user);

        return courseRepository.findActiveCoursesVisibleToUser(userGroups, isAdmin);
    }

    public Page<Course> searchCoursesForEditor(String searchTerm, Pageable pageable) {
        User user = userService.getCurrentUser();
        Set<String> userGroups = user.getGroups();
        boolean isAdmin = authCheckService.isAdmin(user);

        return courseRepository.findCoursesByTitleWithEditorAccess(
            searchTerm,
            userGroups,
            isAdmin,
            pageable
        );
    }
}
```

### Example 2: Advanced Filtering in Resource Layer

```java
@RestController
@RequestMapping("/api/courses")
public class CourseResource {

    private final CourseRepository courseRepository;

    @GetMapping("/search")
    public ResponseEntity<Page<Course>> searchCourses(
        @RequestParam(required = false) String title,
        @RequestParam(required = false) String semester,
        @RequestParam(defaultValue = "false") boolean onlyActive,
        Pageable pageable
    ) {
        User user = getCurrentUser();
        Set<String> userGroups = user.getGroups();
        boolean isAdmin = authCheckService.isAdmin(user);

        // Build specification dynamically based on request parameters
        var spec = CourseSpecs.and(
            CourseSpecs.hasStudentAccess(userGroups, isAdmin),
            CourseSpecs.titleContains(title),              // null if not provided
            CourseSpecs.hasSemester(semester),             // null if not provided
            onlyActive ? CourseSpecs.isActive(ZonedDateTime.now()) : null,
            CourseSpecs.distinct()
        );

        Page<Course> courses = courseRepository.findAll(spec, pageable);
        return ResponseEntity.ok(courses);
    }
}
```

### Example 3: Complex Business Logic

```java
@Service
public class CourseEnrollmentService {

    private final CourseRepository courseRepository;

    /**
     * Find courses that:
     * - Have active enrollment
     * - Are visible to the user
     * - Exclude test courses
     * - Optionally filter by semester
     */
    public List<Course> getEnrollableCourses(User user, String semester) {
        var now = ZonedDateTime.now();
        Set<String> userGroups = user.getGroups();
        boolean isAdmin = authCheckService.isAdmin(user);

        var spec = CourseSpecs.and(
            CourseSpecs.hasActiveEnrollment(now),
            CourseSpecs.isVisibleToUser(userGroups, isAdmin, now),
            CourseSpecs.excludeTestCourses(),
            CourseSpecs.hasSemester(semester),
            CourseSpecs.distinct()
        );

        return courseRepository.findAll(spec);
    }
}
```

## Migration Strategy

### Step 1: Keep Old Queries for Compatibility

```java
// Keep the old @Query method
@Query("""
    SELECT c FROM Course c
    WHERE c.teachingAssistantGroupName IN :userGroups
       OR c.editorGroupName IN :userGroups
       OR c.instructorGroupName IN :userGroups
""")
@Deprecated(forRemoval = true)
List<Course> findAllCoursesByManagementGroupNames(@Param("userGroups") List<String> userGroups);

// Add new specification-based method
default List<Course> findCoursesWithStaffAccess(Set<String> userGroups, boolean isAdmin) {
    var spec = CourseSpecs.hasStaffAccess(userGroups, isAdmin);
    return findAll(spec);
}
```

### Step 2: Update Service Layer Gradually

```java
// Old code
List<Course> courses = courseRepository.findAllCoursesByManagementGroupNames(userGroups);

// New code
List<Course> courses = courseRepository.findCoursesWithStaffAccess(userGroups, isAdmin);
```

### Step 3: Remove Deprecated Methods

Once all usages are migrated, remove the `@Query` annotated methods.

## Pattern from Communication Module

The `MessageSpecs` and `ConversationMessageRepository` in the communication module follow the same pattern:

```java
// MessageSpecs.java - Specification factory
public static Specification<Post> getSearchTextSpecification(String searchText) {
    return (root, query, criteriaBuilder) -> {
        // Implementation
    };
}

// ConversationMessageRepository.java - Repository with default methods
default Page<Post> findMessages(PostContextFilterDTO postContextFilter, Pageable pageable, long userId) {
    var specification = getConversationsSpecification(postContextFilter.conversationIds());
    specification = configureSearchSpecification(specification, postContextFilter, userId);
    return findPostsWithSpecification(pageable, specification);
}

// Service layer
Page<Post> posts = conversationMessageRepository.findMessages(filterDTO, pageable, userId);
```

## Benefits Over Hard-Coded Queries

1. **Single Source of Truth**: Access logic defined once in `CourseSpecs`, referenced everywhere
2. **Composability**: Mix and match specifications to build complex queries
3. **Type Safety**: Compile-time errors instead of runtime SQL errors
4. **Testability**: Easy to unit test individual specifications
5. **Flexibility**: Add/remove filters dynamically based on user input
6. **Maintainability**: Changing access rules requires updating only `CourseSpecs` and `CourseAccessPolicies`

## Testing Specifications

```java
@Test
void testHasStaffAccessSpecification() {
    // Arrange
    Set<String> userGroups = Set.of("course1-tutors", "course2-editors");
    boolean isAdmin = false;
    var spec = CourseSpecs.hasStaffAccess(userGroups, isAdmin);

    // Act
    List<Course> courses = courseRepository.findAll(spec);

    // Assert
    assertThat(courses).allMatch(course ->
        userGroups.contains(course.getTeachingAssistantGroupName()) ||
        userGroups.contains(course.getEditorGroupName()) ||
        userGroups.contains(course.getInstructorGroupName())
    );
}

@Test
void testCombinedSpecifications() {
    // Arrange
    var now = ZonedDateTime.now();
    var spec = CourseSpecs.and(
        CourseSpecs.isActive(now),
        CourseSpecs.hasLearningPathsEnabled()
    );

    // Act
    List<Course> courses = courseRepository.findAll(spec);

    // Assert
    assertThat(courses).allMatch(Course::getLearningPathsEnabled);
    assertThat(courses).allMatch(course ->
        (course.getStartDate() == null || !course.getStartDate().isAfter(now)) &&
        (course.getEndDate() == null || !course.getEndDate().isBefore(now))
    );
}
```

## Next Steps

1. Create specifications for other entities (Exercise, Exam, etc.)
2. Migrate existing hard-coded queries incrementally
3. Update access policies and corresponding specifications together
4. Add integration tests for specification-based queries
5. Document any edge cases or performance considerations

## References

- Communication module: `MessageSpecs.java`, `ConversationMessageRepository.java`
- User search: `UserSpecs.java`, `UserRepository.java`
- Access policies: `CourseAccessPolicies.java`
- Spring Data JPA Specifications: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#specifications