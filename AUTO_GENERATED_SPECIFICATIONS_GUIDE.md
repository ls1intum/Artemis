# Auto-Generated Specifications from Access Policies

This guide demonstrates how SQL queries are **automatically generated** from access policy definitions, creating a true single source of truth.

## The Problem (Before)

Previously, access policies and SQL queries had to be manually synchronized:

```java
// Policy definition in CourseAccessPolicies.java
when(memberOfGroup(Course::getTeachingAssistantGroupName)
    .or(memberOfGroup(Course::getEditorGroupName))
    .or(memberOfGroup(Course::getInstructorGroupName))
    .or(isAdmin()))
    .thenAllow()

// Manually written SQL query in CourseRepository.java
// NOTE: This query mirrors the courseStaffAccessPolicy  ← MANUAL SYNC REQUIRED!
@Query("""
    SELECT c FROM Course c
    WHERE c.teachingAssistantGroupName IN :userGroups
       OR c.editorGroupName IN :userGroups
       OR c.instructorGroupName IN :userGroups
       OR :isAdmin = TRUE
    """)
```

**Problems:**
- Policies and queries can drift apart
- Changes require updating both locations
- No compile-time guarantees of synchronization
- Prone to human error

## The Solution (Auto-Generated)

Now policies **automatically generate** SQL queries:

```java
// 1. Define policy ONCE in CourseAccessPolicies.java
when(memberOfGroup(Course::getTeachingAssistantGroupName, Course_.teachingAssistantGroupName)
    .or(memberOfGroup(Course::getEditorGroupName, Course_.editorGroupName))
    .or(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName))
    .or(isAdmin()))
    .thenAllow()

// 2. Use auto-generated specification (NO manual SQL needed!)
Specification<Course> spec = policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin);
List<Course> courses = courseRepository.findAll(spec);
```

**Benefits:**
- **Single source of truth:** Policy defines BOTH runtime checks AND queries
- **Automatic sync:** Changing the policy updates queries automatically
- **Type-safe:** Compile-time checks via JPA Criteria API
- **No duplication:** Write access logic once, use everywhere

## How It Works

### Step 1: Enhanced Conditions (SpecificationConditions)

Conditions now support **dual modes** - runtime checks AND query generation:

```java
// Old: Runtime-only condition
memberOfGroup(Course::getInstructorGroupName)  // Only works at runtime

// New: Dual-mode condition
memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName)
//            ↑ Runtime getter                 ↑ Query metamodel attribute

// This single condition can:
// 1. Check user access at runtime: condition.test(user, course)
// 2. Generate SQL WHERE clause: condition.toPredicate(root, cb, userGroups, isAdmin)
```

### Step 2: Policy to Specification Conversion

Access policies can now convert themselves to JPA Specifications:

```java
// Policy definition
AccessPolicy<Course> policy = AccessPolicy.forResource(Course.class)
    .rule(when(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName)
        .or(isAdmin()))
        .thenAllow())
    .denyByDefault();

// Auto-generate specification
Specification<Course> spec = policy.toSpecification(userGroups, isAdmin);

// Use in queries
List<Course> courses = courseRepository.findAll(spec);
```

### Step 3: Convenient Wrapper (PolicyBasedCourseSpecs)

A Spring bean provides convenient access to all policy-based specifications:

```java
@Component
public class PolicyBasedCourseSpecs {

    private final AccessPolicy<Course> courseStaffAccessPolicy;

    public Specification<Course> withStaffAccess(Set<String> userGroups, boolean isAdmin) {
        return courseStaffAccessPolicy.toSpecification(userGroups, isAdmin);
    }

    // More policy-based specs...
}
```

## Complete Example: courseStaffAccessPolicy

### 1. Define Policy (CourseAccessPolicies.java)

```java
@Bean
@Lazy
public AccessPolicy<Course> courseStaffAccessPolicy() {
    return AccessPolicy.forResource(Course.class)
        .named("course-staff-access")
        .rule(when(memberOfGroup(Course::getTeachingAssistantGroupName, Course_.teachingAssistantGroupName)
                .or(memberOfGroup(Course::getEditorGroupName, Course_.editorGroupName))
                .or(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName))
                .or(isAdmin()))
            .thenAllow()
            .documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT))
        .denyByDefault();
}
```

### 2. Get Specification (PolicyBasedCourseSpecs.java)

```java
@Component
public class PolicyBasedCourseSpecs {

    private final AccessPolicy<Course> courseStaffAccessPolicy;

    public PolicyBasedCourseSpecs(AccessPolicy<Course> courseStaffAccessPolicy, ...) {
        this.courseStaffAccessPolicy = courseStaffAccessPolicy;
    }

    /**
     * Auto-generated from courseStaffAccessPolicy.
     * Changes to the policy automatically update this specification!
     */
    public Specification<Course> withStaffAccess(Set<String> userGroups, boolean isAdmin) {
        return courseStaffAccessPolicy.toSpecification(userGroups, isAdmin);
    }
}
```

### 3. Use in Service Layer

```java
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final PolicyBasedCourseSpecs policyBasedCourseSpecs;

    public List<Course> getStaffCourses(User user) {
        Set<String> userGroups = user.getGroups();
        boolean isAdmin = authCheck.isAdmin(user);

        // Auto-generated specification from policy!
        Specification<Course> spec = policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin);
        return courseRepository.findAll(spec);
    }
}
```

### 4. Combine with Other Filters

```java
// Find staff courses that haven't ended
var now = ZonedDateTime.now();
var spec = CourseSpecs.and(
    policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin),  // Auto-generated from policy
    CourseSpecs.isNotEnded(now),                                   // Additional filter
    CourseSpecs.distinct()
);
List<Course> courses = courseRepository.findAll(spec);
```

## All Available Policy-Based Specifications

| Specification Method | Policy Source | Allows |
|---------------------|---------------|--------|
| `withVisibilityAccess(...)` | `courseVisibilityPolicy` | Staff always; students if started |
| `withStudentAccess(...)` | `courseStudentAccessPolicy` | Any enrolled user or admin |
| `withStaffAccess(...)` | `courseStaffAccessPolicy` | TA, editor, instructor, admin |
| `withEditorAccess(...)` | `courseEditorAccessPolicy` | Editor, instructor, admin |
| `withInstructorAccess(...)` | `courseInstructorAccessPolicy` | Instructor, admin |

## Updating Access Rules

**When you change a policy, queries update automatically!**

### Example: Adding a New Role

```java
// 1. Update the policy definition
@Bean
@Lazy
public AccessPolicy<Course> courseStaffAccessPolicy() {
    return AccessPolicy.forResource(Course.class)
        .rule(when(
            memberOfGroup(Course::getTeachingAssistantGroupName, Course_.teachingAssistantGroupName)
                .or(memberOfGroup(Course::getEditorGroupName, Course_.editorGroupName))
                .or(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName))
                .or(memberOfGroup(Course::getModeratorGroupName, Course_.moderatorGroupName))  // NEW!
                .or(isAdmin()))
            .thenAllow())
        .denyByDefault();
}

// 2. That's it! All queries using withStaffAccess() are automatically updated!
```

**No need to:**
- ❌ Update SQL queries manually
- ❌ Find all locations that need changes
- ❌ Risk missing a query
- ✅ Just update the policy - queries follow automatically!

## Architecture

```
┌─────────────────────────────────────────────┐
│   CourseAccessPolicies.java                 │
│   (Policy Definitions)                      │
│                                             │
│   Define access rules ONCE using            │
│   SpecificationConditions                   │
└──────────────────┬──────────────────────────┘
                   │
                   │ toSpecification()
                   ↓
┌─────────────────────────────────────────────┐
│   PolicyBasedCourseSpecs.java               │
│   (Specification Factory)                   │
│                                             │
│   Wraps policies, provides convenient       │
│   methods for getting specifications        │
└──────────────────┬──────────────────────────┘
                   │
                   │ inject & use
                   ↓
┌─────────────────────────────────────────────┐
│   Service Layer / Repository                │
│                                             │
│   Use auto-generated specifications         │
│   in database queries                       │
└─────────────────────────────────────────────┘
```

## Technical Details

### SpecificationCondition Interface

Extends `PolicyCondition` with query generation capability:

```java
public interface SpecificationCondition<T> extends PolicyCondition<T> {

    // Runtime check (from PolicyCondition)
    boolean test(User user, T resource);

    // Query generation (new!)
    Predicate toPredicate(Root<T> root, CriteriaBuilder cb, Set<String> userGroups, boolean isAdmin);
}
```

### Condition Implementations

Each condition implements both modes:

```java
public static <T> SpecificationCondition<T> memberOfGroup(
    Function<T, String> groupExtractor,      // For runtime
    SingularAttribute<T, String> groupAttribute  // For queries
) {
    return new SpecificationCondition<>() {
        @Override
        public boolean test(User user, T resource) {
            // Runtime: check user's groups
            String group = groupExtractor.apply(resource);
            return group != null && user.getGroups().contains(group);
        }

        @Override
        public Predicate toPredicate(Root<T> root, CriteriaBuilder cb,
                                     Set<String> userGroups, boolean isAdmin) {
            // Query: generate WHERE groupAttribute IN :userGroups
            return root.get(groupAttribute).in(userGroups);
        }
    };
}
```

### Composition

Conditions compose naturally using `.and()` and `.or()`:

```java
// This works for BOTH runtime checks and query generation!
memberOfGroup(Course::getEditorGroupName, Course_.editorGroupName)
    .or(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName))
    .or(isAdmin())

// Runtime: checks if user is editor OR instructor OR admin
// Query: generates WHERE (editorGroupName IN :groups OR instructorGroupName IN :groups OR :isAdmin = TRUE)
```

## Migration Strategy

### Phase 1: Add SpecificationConditions (Parallel)

```java
// Keep old conditions for compatibility
import static de.tum.cit.aet.artemis.core.security.policy.Conditions.*;

// Add new specification conditions alongside
import static de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions.*;
```

### Phase 2: Update Policies

```java
// Old policy (runtime only)
when(memberOfGroup(Course::getInstructorGroupName))
    .thenAllow()

// New policy (runtime + queries)
when(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName))
    .thenAllow()
```

### Phase 3: Replace Manual Queries

```java
// Old: Manual query
@Query("""
    SELECT c FROM Course c
    WHERE c.instructorGroupName IN :userGroups
       OR :isAdmin = TRUE
    """)
List<Course> findCoursesForInstructor(@Param("userGroups") Set<String> userGroups,
                                       @Param("isAdmin") boolean isAdmin);

// New: Auto-generated from policy
List<Course> courses = courseRepository.findAll(
    policyBasedCourseSpecs.withInstructorAccess(userGroups, isAdmin)
);
```

### Phase 4: Remove Deprecated Queries

Once all usages migrated, remove the `@Query` methods.

## Testing

### Test Policy-To-Specification Conversion

```java
@Test
void testStaffAccessPolicyGeneratesCorrectSpecification() {
    // Arrange
    Set<String> userGroups = Set.of("course1-tutors");
    boolean isAdmin = false;

    // Act: Auto-generate spec from policy
    Specification<Course> spec = policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin);
    List<Course> courses = courseRepository.findAll(spec);

    // Assert: Should match courses where user is TA
    assertThat(courses).allMatch(course ->
        userGroups.contains(course.getTeachingAssistantGroupName())
    );
}
```

### Test Runtime and Query Consistency

```java
@Test
void testPolicyConsistencyBetweenRuntimeAndQuery() {
    // Arrange
    User user = createUserWithGroups("course1-tutors");
    Set<String> userGroups = user.getGroups();
    boolean isAdmin = false;

    // Act: Get courses using auto-generated spec
    Specification<Course> spec = policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin);
    List<Course> coursesFromQuery = courseRepository.findAll(spec);

    // Assert: Runtime policy check should match query results
    for (Course course : allCourses) {
        boolean allowedByPolicy = courseStaffAccessPolicy.evaluate(user, course) == PolicyEffect.ALLOW;
        boolean foundInQuery = coursesFromQuery.contains(course);

        assertThat(allowedByPolicy).isEqualTo(foundInQuery);
    }
}
```

## Advantages Over Manual Specifications

### Before (Manual CourseSpecs)

```java
// CourseSpecs.java - manually written
public static Specification<Course> hasStaffAccess(Set<String> userGroups, boolean isAdmin) {
    return (root, query, criteriaBuilder) -> {
        if (isAdmin) {
            return criteriaBuilder.conjunction();
        }
        Predicate taGroup = root.get(Course_.TEACHING_ASSISTANT_GROUP_NAME).in(userGroups);
        Predicate editorGroup = root.get(Course_.EDITOR_GROUP_NAME).in(userGroups);
        Predicate instructorGroup = root.get(Course_.INSTRUCTOR_GROUP_NAME).in(userGroups);
        return criteriaBuilder.or(taGroup, editorGroup, instructorGroup);
    };
}
```

**Problems:**
- Must manually sync with policy definition
- No compile-time guarantee of correctness
- Easy to make mistakes (e.g., forget a group)

### After (Auto-Generated)

```java
// PolicyBasedCourseSpecs.java - auto-generated from policy
public Specification<Course> withStaffAccess(Set<String> userGroups, boolean isAdmin) {
    return courseStaffAccessPolicy.toSpecification(userGroups, isAdmin);
    // ↑ Automatically reflects policy definition!
}
```

**Advantages:**
- ✅ Automatically synced with policy
- ✅ Compile-time type safety
- ✅ Single source of truth
- ✅ No manual synchronization needed

## Best Practices

1. **Use SpecificationConditions for new policies** that need query generation
2. **Keep Conditions for runtime-only policies** (e.g., complex business logic that can't be expressed in SQL)
3. **Inject PolicyBasedCourseSpecs** in services that need database filtering
4. **Combine with CourseSpecs** for additional filtering (temporal, feature flags, etc.)
5. **Document which policy is used** when using auto-generated specs
6. **Test both runtime and query behavior** to ensure consistency

## Future Enhancements

- **Code generation:** Generate PolicyBasedCourseSpecs automatically from policy beans
- **Performance optimization:** Cache compiled specifications
- **Query analysis:** Tooling to show which SQL is generated from policies
- **Multi-entity policies:** Support policies that span multiple entities with JOINs

## Conclusion

Auto-generated specifications create a **true single source of truth** for access control:

- **Define once:** Write access logic in the policy
- **Use everywhere:** Same logic for runtime checks and database queries
- **Update once:** Changing the policy updates all queries automatically
- **Type-safe:** Compile-time guarantees via JPA Criteria API

**Result:** More maintainable, less error-prone access control!