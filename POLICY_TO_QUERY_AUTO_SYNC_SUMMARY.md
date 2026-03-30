# Automatic Query Generation from Access Policies - Implementation Summary

## Achievement: Single Source of Truth

**You now have automatic synchronization between access policies and SQL queries!**

When you update an access policy in `CourseAccessPolicies.java`, the database queries are **automatically updated** without any manual changes to SQL or specifications.

## What Was Implemented

### 1. **SpecificationCondition Interface**
Location: `src/main/java/de/tum/cit/aet/artemis/core/security/policy/SpecificationCondition.java`

Enables policy conditions to work in **two modes**:
- **Runtime mode:** Check if a user has access to a resource
- **Query mode:** Generate SQL WHERE clauses for database filtering

```java
public interface SpecificationCondition<T> extends PolicyCondition<T> {
    // Runtime check (inherited)
    boolean test(User user, T resource);

    // Query generation (new!)
    Predicate toPredicate(Root<T> root, CriteriaBuilder cb, Set<String> userGroups, boolean isAdmin);
}
```

### 2. **SpecificationConditions (Dual-Mode Conditions)**
Location: `src/main/java/de/tum/cit/aet/artemis/core/security/policy/SpecificationConditions.java`

Enhanced conditions that support both runtime checks and query generation:

```java
// Old: Runtime only
memberOfGroup(Course::getInstructorGroupName)

// New: Runtime + Query generation
memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName)
//            ↑ runtime getter               ↑ query metamodel attribute
```

**Available conditions:**
- `memberOfGroup(getter, attribute)` - Check/filter by group membership
- `hasStarted(getter, attribute)` - Check/filter by start date
- `hasNotEnded(getter, attribute)` - Check/filter by end date
- `isAdmin()` - Check/filter for admin users
- `always()` / `never()` - Always true/false conditions

### 3. **AccessPolicy.toSpecification() Method**
Location: `src/main/java/de/tum/cit/aet/artemis/core/security/policy/AccessPolicy.java`

Converts access policies to JPA Specifications automatically:

```java
// Policy definition
AccessPolicy<Course> policy = ...;

// Auto-generate specification
Specification<Course> spec = policy.toSpecification(userGroups, isAdmin);

// Use in queries
List<Course> courses = courseRepository.findAll(spec);
```

### 4. **Updated CourseAccessPolicies**
Location: `src/main/java/de/tum/cit/aet/artemis/core/security/policy/definitions/CourseAccessPolicies.java`

All course access policies now use `SpecificationConditions`:

```java
@Bean
public AccessPolicy<Course> courseStaffAccessPolicy() {
    return AccessPolicy.forResource(Course.class)
        .rule(when(memberOfGroup(Course::getTeachingAssistantGroupName, Course_.teachingAssistantGroupName)
                .or(memberOfGroup(Course::getEditorGroupName, Course_.editorGroupName))
                .or(memberOfGroup(Course::getInstructorGroupName, Course_.instructorGroupName))
                .or(isAdmin()))
            .thenAllow())
        .denyByDefault();
}
```

### 5. **PolicyBasedCourseSpecs Component**
Location: `src/main/java/de/tum/cit/aet/artemis/core/repository/PolicyBasedCourseSpecs.java`

Spring bean that provides convenient access to auto-generated specifications:

```java
@Component
public class PolicyBasedCourseSpecs {
    // Auto-injected policy beans
    private final AccessPolicy<Course> courseStaffAccessPolicy;
    // ... more policies

    // Auto-generate specs from policies
    public Specification<Course> withStaffAccess(Set<String> userGroups, boolean isAdmin) {
        return courseStaffAccessPolicy.toSpecification(userGroups, isAdmin);
    }

    public Specification<Course> withEditorAccess(Set<String> userGroups, boolean isAdmin) {
        return courseEditorAccessPolicy.toSpecification(userGroups, isAdmin);
    }
    // ... more auto-generated specs
}
```

### 6. **CourseSpecs (Static Utility Specifications)**
Location: `src/main/java/de/tum/cit/aet/artemis/core/repository/CourseSpecs.java`

Static utility specifications for non-policy filtering (temporal, feature flags, etc.):

```java
// Temporal filters
CourseSpecs.isActive(now)
CourseSpecs.isNotEnded(now)
CourseSpecs.hasActiveEnrollment(now)

// Feature flags
CourseSpecs.hasLearningPathsEnabled()

// Search
CourseSpecs.titleContains(searchTerm)
CourseSpecs.hasSemester(semester)

// Utility
CourseSpecs.distinct()
CourseSpecs.and(spec1, spec2, ...)
CourseSpecs.or(spec1, spec2, ...)
```

### 7. **Documentation**
- `SPECIFICATION_PATTERN_GUIDE.md` - General guide to Specification pattern
- `AUTO_GENERATED_SPECIFICATIONS_GUIDE.md` - Detailed guide to auto-generated specs
- This file - Implementation summary

## How to Use

### Basic Usage (Service Layer)

```java
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final PolicyBasedCourseSpecs policyBasedCourseSpecs;  // Inject this

    public List<Course> getStaffCourses(User user) {
        Set<String> userGroups = user.getGroups();
        boolean isAdmin = authCheck.isAdmin(user);

        // Auto-generated from courseStaffAccessPolicy
        var spec = policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin);
        return courseRepository.findAll(spec);
    }
}
```

### Combining Specifications

```java
// Find staff courses that haven't ended, with title search
var now = ZonedDateTime.now();
var spec = CourseSpecs.and(
    policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin),  // Policy-based
    CourseSpecs.isNotEnded(now),                                   // Temporal filter
    CourseSpecs.titleContains(searchTerm),                         // Search filter
    CourseSpecs.distinct()
);
List<Course> courses = courseRepository.findAll(spec, pageable);
```

### With Pagination

```java
Page<Course> courses = courseRepository.findAll(
    policyBasedCourseSpecs.withEditorAccess(userGroups, isAdmin),
    PageRequest.of(0, 20)
);
```

## The Magic: Automatic Updates

### Before (Manual Sync Required)

```java
// 1. Update policy in CourseAccessPolicies.java
when(memberOfGroup(Course::getTeachingAssistantGroupName)
    .or(memberOfGroup(Course::getEditorGroupName))
    .or(memberOfGroup(Course::getModeratorGroupName))  // NEW ROLE!
    .or(isAdmin()))

// 2. Update ALL queries manually in CourseRepository.java
@Query("""
    SELECT c FROM Course c
    WHERE c.teachingAssistantGroupName IN :groups
       OR c.editorGroupName IN :groups
       OR c.moderatorGroupName IN :groups  -- DON'T FORGET THIS!
       OR :isAdmin = TRUE
    """)

// 3. Update manual specifications in CourseSpecs.java
public static Specification<Course> hasStaffAccess(...) {
    Predicate moderatorGroup = root.get(Course_.MODERATOR_GROUP_NAME).in(userGroups);  // DON'T FORGET THIS!
    return cb.or(taGroup, editorGroup, moderatorGroup, instructorGroup);
}

// ❌ Error-prone: Easy to miss a location
// ❌ Time-consuming: Must find and update all queries
// ❌ No guarantees: No compile-time check that all are updated
```

### After (Automatic Sync)

```java
// 1. Update policy in CourseAccessPolicies.java
when(memberOfGroup(Course::getTeachingAssistantGroupName, Course_.teachingAssistantGroupName)
    .or(memberOfGroup(Course::getEditorGroupName, Course_.editorGroupName))
    .or(memberOfGroup(Course::getModeratorGroupName, Course_.moderatorGroupName))  // NEW ROLE!
    .or(isAdmin()))

// 2. That's it! All queries are automatically updated!

// ✅ No manual query updates needed
// ✅ All usages of withStaffAccess() automatically include the new role
// ✅ Compile-time type safety ensures correctness
```

## Available Policy-Based Specifications

| Method | Policy | Allows |
|--------|--------|--------|
| `withVisibilityAccess()` | `courseVisibilityPolicy` | Staff always; students if started |
| `withStudentAccess()` | `courseStudentAccessPolicy` | Any enrolled user or admin |
| `withStaffAccess()` | `courseStaffAccessPolicy` | TA, editor, instructor, admin |
| `withEditorAccess()` | `courseEditorAccessPolicy` | Editor, instructor, admin |
| `withInstructorAccess()` | `courseInstructorAccessPolicy` | Instructor, admin |

**Each of these automatically reflects changes to their corresponding policy!**

## Architecture Flow

```
┌─────────────────────────────────────────┐
│  CourseAccessPolicies.java              │
│  Define access rules ONCE               │
│  using SpecificationConditions          │
└──────────────┬──────────────────────────┘
               │
               │ @Bean injection
               ↓
┌─────────────────────────────────────────┐
│  PolicyBasedCourseSpecs.java            │
│  Wraps policies, calls toSpecification()│
└──────────────┬──────────────────────────┘
               │
               │ inject into services
               ↓
┌─────────────────────────────────────────┐
│  Service Layer                          │
│  Use auto-generated specifications      │
│  in database queries                    │
└─────────────────────────────────────────┘
```

## Migration Path

### For New Code

**Always use policy-based specifications:**

```java
// ✅ Good: Auto-generated from policy
var spec = policyBasedCourseSpecs.withStaffAccess(userGroups, isAdmin);

// ❌ Bad: Manual query that can drift
@Query("SELECT c FROM Course c WHERE c.teachingAssistantGroupName IN :groups ...")
```

### For Existing Code

**Gradual migration:**

1. Keep existing `@Query` methods for now
2. Add new policy-based alternatives
3. Migrate usages incrementally
4. Remove old queries once all usages migrated

```java
// Phase 1: Keep old (deprecated)
@Deprecated(forRemoval = true)
@Query("SELECT c FROM Course c WHERE ...")
List<Course> findCoursesForStaff(...);

// Phase 2: Add new policy-based
default List<Course> findCoursesWithStaffAccess(Set<String> groups, boolean isAdmin,
                                                PolicyBasedCourseSpecs policyBasedCourseSpecs) {
    return findAll(policyBasedCourseSpecs.withStaffAccess(groups, isAdmin));
}

// Phase 3: Migrate usages
// Old: courseRepository.findCoursesForStaff(groups, isAdmin);
// New: courseRepository.findAll(policyBasedCourseSpecs.withStaffAccess(groups, isAdmin));

// Phase 4: Remove deprecated method
```

## Testing

### Test Policy-to-Query Consistency

```java
@Test
void testPolicyAndQueryAreConsistent() {
    // Arrange
    User user = createUserWithGroups("course1-tutors");
    Course course = createCourse();

    // Act: Runtime check
    boolean allowedByPolicy = courseStaffAccessPolicy.evaluate(user, course) == PolicyEffect.ALLOW;

    // Act: Query check
    var spec = policyBasedCourseSpecs.withStaffAccess(user.getGroups(), false);
    boolean foundInQuery = courseRepository.findAll(spec).contains(course);

    // Assert: Must match!
    assertThat(allowedByPolicy).isEqualTo(foundInQuery);
}
```

## Key Files Summary

| File | Purpose |
|------|---------|
| `SpecificationCondition.java` | Interface for dual-mode conditions |
| `SpecificationConditions.java` | Dual-mode condition implementations |
| `AccessPolicy.java` | Added `toSpecification()` method |
| `CourseAccessPolicies.java` | Updated to use SpecificationConditions |
| `PolicyBasedCourseSpecs.java` | Spring bean providing auto-generated specs |
| `CourseSpecs.java` | Static utility specifications (temporal, search, etc.) |
| `CourseRepository.java` | Extended with JpaSpecificationExecutor, example usages |

## Benefits Achieved

✅ **Single Source of Truth:** Access logic defined once in policies
✅ **Automatic Synchronization:** Updating policies automatically updates queries
✅ **Type Safety:** Compile-time checks via JPA Criteria API
✅ **Composability:** Mix policy-based and utility specifications
✅ **Maintainability:** No manual SQL/specification synchronization
✅ **Less Error-Prone:** Impossible to have policy-query drift

## Next Steps for Other Entities

This pattern can be extended to other entities (Exercise, Exam, etc.):

1. Create `SpecificationConditions` for entity-specific checks
2. Update access policies to use `SpecificationConditions`
3. Create `PolicyBased[Entity]Specs` component
4. Use auto-generated specs in services

## Conclusion

You now have **truly automatic synchronization** between access policies and database queries.

**Change the policy → Queries update automatically!**

No more manual SQL updates, no more risk of drift, single source of truth for all access control logic.