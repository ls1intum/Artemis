# Milestone 4: Frontend Simplification - Detailed Implementation Plan

## Overview
Transform the Angular frontend from a complex 3-tier (Global → Course → Exercise) × 8-feature settings system to a simple single-tier course-level configuration that matches the new backend API.

**Complexity Reduction:** 24 configuration surfaces → 1 unified interface

---

## Phase 1: Update Models & DTOs (Foundation)

### 1.1 Create New TypeScript Models
**File:** `src/main/webapp/app/iris/shared/entities/settings/iris-course-settings.model.ts` (NEW)

Create TypeScript interfaces matching the new backend DTOs:
```typescript
export interface IrisPipelineVariant {
    id: 'DEFAULT' | 'ADVANCED';
}

export interface IrisRateLimitConfiguration {
    requests?: number;
    timeframeHours?: number;
}

export interface IrisCourseSettingsDTO {
    enabled: boolean;
    customInstructions?: string;
    variant: IrisPipelineVariant;
    rateLimit: IrisRateLimitConfiguration;
}

export interface CourseIrisSettingsDTO {
    courseId: number;
    settings: IrisCourseSettingsDTO;
    effectiveRateLimit: IrisRateLimitConfiguration;
    applicationRateLimitDefaults: IrisRateLimitConfiguration;
}
```

### 1.2 Mark Legacy Models as Deprecated
**Files to mark @deprecated:**
- `iris-settings.model.ts` (all classes: IrisSettings, IrisGlobalSettings, IrisCourseSettings, IrisExerciseSettings)
- `iris-sub-settings.model.ts` (all sub-settings classes)
- `iris-combined-settings-update-dto.model.ts` (if exists)

Add deprecation comments pointing to new models but don't delete yet (needed during transition).

---

## Phase 2: Refactor Settings Service

### 2.1 Update `iris-settings.service.ts`

**Remove methods:**
- `getGlobalSettings()`
- `setGlobalSettings()`
- `getUncombinedExerciseSettings()`
- `getCombinedExerciseSettings()`
- `setExerciseSettings()`
- `getUncombinedCourseSettings()` (replaced by direct GET)

**Update methods:**
```typescript
// Replace getCombinedCourseSettings with simple GET
getCourseSettings(courseId: number): Observable<CourseIrisSettingsDTO> {
    const url = `api/iris/courses/${courseId}/iris-settings`;
    // Keep 5-minute cache but simplify (no inheritance merging needed)
}

// Replace setCourseSettings
updateCourseSettings(courseId: number, settings: IrisCourseSettingsDTO): Observable<HttpResponse<CourseIrisSettingsDTO>> {
    const url = `api/iris/courses/${courseId}/iris-settings`;
    // Invalidate cache on success
}

// Keep variant fetching (still needed)
getVariants(): Observable<IrisPipelineVariant[]> {
    return of([
        { id: 'DEFAULT' },
        { id: 'ADVANCED' }
    ]);
}
```

**Simplify caching:**
- Remove exercise settings cache
- Keep course settings cache (5-minute TTL)
- Remove "combined vs uncombined" complexity

### 2.2 Delete `iris-empty-settings.service.ts`
No longer needed - single settings object is always populated by backend.

---

## Phase 3: Redesign Settings UI Component

### 3.1 Refactor `iris-settings-update.component.ts`

**Current:** Generic component handling 3 types × 8 features
**New:** Course-specific component with single unified form

**Template structure (new):**
```html
<div class="course-iris-settings">
  <h2>Iris Settings for Course</h2>

  <!-- Enable/Disable Toggle -->
  <div class="enable-toggle">
    <button [class.active]="settings.enabled" (click)="setEnabled(true)">
      Enabled
    </button>
    <button [class.active]="!settings.enabled" (click)="setEnabled(false)">
      Disabled
    </button>
  </div>

  <!-- Custom Instructions (Instructors + Admins) -->
  <div class="custom-instructions">
    <label>Custom Instructions (shared across all Iris features)</label>
    <textarea [(ngModel)]="settings.customInstructions"
              maxlength="2048"
              placeholder="Optional instructions for Iris...">
    </textarea>
    <span class="char-counter">{{settings.customInstructions?.length || 0}} / 2048</span>
  </div>

  <!-- Admin-Only Section -->
  <div class="admin-section" *ngIf="isAdmin">
    <h3>Administrator Settings</h3>

    <!-- Pipeline Variant -->
    <div class="variant-selector">
      <label>Pipeline Variant</label>
      <select [(ngModel)]="settings.variant">
        <option value="DEFAULT">Default</option>
        <option value="ADVANCED">Advanced</option>
      </select>
    </div>

    <!-- Rate Limit Overrides -->
    <div class="rate-limits">
      <label>Rate Limit Overrides (0 = unlimited/use defaults)</label>
      <input type="number"
             [(ngModel)]="settings.rateLimit.requests"
             placeholder="Requests (default: {{applicationDefaults.requests || 0}})">
      <input type="number"
             [(ngModel)]="settings.rateLimit.timeframeHours"
             placeholder="Timeframe Hours (default: {{applicationDefaults.timeframeHours || 0}})">
    </div>

    <!-- Effective Rate Limit Display -->
    <div class="effective-limits">
      <p>Effective: {{effectiveRateLimit.requests || 'unlimited'}} requests per
         {{effectiveRateLimit.timeframeHours || '∞'}} hours</p>
    </div>
  </div>

  <!-- Save/Cancel -->
  <div class="actions">
    <button (click)="save()" [disabled]="!isDirty()">Save</button>
    <button (click)="reload()" [class.warn]="isDirty()">Reload</button>
  </div>
</div>
```

**Component logic:**
- Load `CourseIrisSettingsDTO` via service
- Extract `settings` (editable) and `effectiveRateLimit` + `applicationRateLimitDefaults` (display)
- Simple dirty checking (compare current vs. initial)
- Permission check: `isAdmin = accountService.hasAnyAuthorityDirect(['ROLE_ADMIN'])`
- Validate: Instructors can only change `enabled` and `customInstructions`
- `canDeactivate` guard for unsaved changes

### 3.2 Delete `iris-common-sub-settings-update.component`
Replaced by simpler inline controls in main component.

---

## Phase 4: Simplify Quick Toggle Component

### 4.1 Refactor `iris-enabled.component.ts`

**Current:** Complex logic with "partial" state for 8 features, ALL subsettings toggle
**New:** Simple binary enabled/disabled toggle

**Remove:**
- `IrisSubSettingsType.ALL` logic (no more 8 features)
- Category filtering
- "CUSTOM" button state (partial enablement)
- Feature-specific subsetting detection

**Keep:**
- Green ON / Red OFF buttons
- Quick toggle for instructors
- Link to settings page

**New template:**
```html
<div class="iris-enabled-indicator">
  <button [class.enabled]="isEnabled"
          [class.disabled]="!isEnabled"
          (click)="toggleEnabled(true)"
          [disabled]="!canEdit">
    {{ isEnabled ? 'ON' : 'OFF' }}
  </button>
  <a *ngIf="canEdit" [routerLink]="settingsRoute">Configure</a>
</div>
```

**Component logic:**
- Load course settings via `irisSettingsService.getCourseSettings(courseId)`
- Display `settings.enabled` state
- Quick toggle updates only the `enabled` flag
- Remove all feature-type complexity

---

## Phase 5: Update Wrapper Components & Routes

### 5.1 Delete Global Settings
**Remove files:**
- `src/main/webapp/app/iris/manage/settings/iris-global-settings-update/` (entire directory)
- Route in `admin.routes.ts`: `/admin/iris`

**Rationale:** No more global settings in new architecture.

### 5.2 Simplify Course Settings
**Keep:** `iris-course-settings-update.component.ts`
**Update:** Now directly uses refactored `IrisSettingsUpdateComponent`
**Route:** `/course-management/:courseId/iris-settings` (unchanged)

### 5.3 Delete Exercise Settings
**Remove files:**
- `src/main/webapp/app/iris/manage/settings/iris-exercise-settings-update/` (entire directory)
- Routes in exercise route files referencing `iris-settings`

**Rationale:** No more exercise-level overrides.

---

## Phase 6: Update Integration Points

### 6.1 Control Center Component
**File:** `control-center.component.html`

**Update:**
```html
<!-- Before: -->
<jhi-iris-enabled
    [course]="course()"
    [irisSubSettingsType]="IrisSubSettingsType.ALL"
    [showCustomButton]="true" />

<!-- After: -->
<jhi-iris-enabled [course]="course()" />
<!-- Component internally links to /course-management/{{courseId}}/iris-settings -->
```

### 6.2 Exercise Detail Pages
**Files:** Programming exercise detail components

**Remove:** Exercise-level `IrisEnabledComponent` instances
**Rationale:** Exercises no longer have individual settings; rely on course-level enabled flag.

### 6.3 Competency Management
**Keep:** Course-level `IrisEnabledComponent` for competency generation
**Update:** Uses simplified component (no feature-type parameter)

---

## Phase 7: Update Tests

### 7.1 Update Mock Data
**File:** `src/test/javascript/spec/helpers/mocks/iris/mock-settings.ts`

**Replace mock structures:**
```typescript
export const mockCourseIrisSettings: CourseIrisSettingsDTO = {
    courseId: 1,
    settings: {
        enabled: true,
        customInstructions: 'Test instructions',
        variant: 'DEFAULT',
        rateLimit: { requests: null, timeframeHours: null }
    },
    effectiveRateLimit: { requests: 15, timeframeHours: 2 },
    applicationRateLimitDefaults: { requests: 15, timeframeHours: 2 }
};
```

### 7.2 Update Component Specs
**Files to update:**
- `iris-settings-update.component.spec.ts` - Simplify to test single course form
- `iris-enabled.component.spec.ts` - Remove feature-type tests
- `control-center.component.spec.ts` - Update Iris section tests

**Remove specs:**
- `iris-global-settings-update.component.spec.ts`
- `iris-exercise-settings-update.component.spec.ts`
- `iris-common-sub-settings-update.component.spec.ts`

### 7.3 Update Service Specs
**File:** `iris-settings.service.spec.ts`

**Update tests:**
- Remove global/exercise endpoint tests
- Update course settings GET/PUT tests
- Test cache behavior (simplified)
- Test permission checks (admin vs instructor)

---

## Phase 8: Translation Cleanup

### 8.1 Remove Obsolete Keys
**Files:** `src/main/webapp/i18n/en/iris.json` (and other locales)

**Remove:**
- `artemisApp.iris.settings.title.global`
- `artemisApp.iris.settings.title.exercise`
- `artemisApp.iris.settings.subSettings.*` (8 feature-specific groups)
- `artemisApp.iris.settings.inheritAllowedVariants`
- `artemisApp.iris.settings.categories.*`
- `artemisApp.iris.settings.proactiveEvents.*`

### 8.2 Add New Keys
```json
{
  "artemisApp.iris.settings": {
    "title": "Iris Settings",
    "enabled": "Enable Iris",
    "disabled": "Disable Iris",
    "customInstructions": "Custom Instructions (shared)",
    "customInstructionsHelp": "These instructions apply to all Iris chat contexts",
    "variant": "Pipeline Variant",
    "rateLimit": "Rate Limit Override",
    "rateLimitHelp": "Leave at 0 to use application defaults",
    "effectiveRateLimit": "Effective Rate Limit",
    "applicationDefaults": "Application Defaults"
  }
}
```

---

## Phase 9: Verification & Testing

### 9.1 Manual Testing Checklist
- [ ] Course settings page loads correctly
- [ ] Instructors can toggle enabled/disabled
- [ ] Instructors can edit custom instructions
- [ ] Instructors CANNOT change variant or rate limits
- [ ] Admins CAN change variant and rate limits
- [ ] Effective rate limits display correctly
- [ ] Save/reload buttons work
- [ ] Dirty state detection works
- [ ] Control center toggle works
- [ ] Old routes redirect properly

### 9.2 Automated Tests
- Run all frontend unit tests: `npm test`
- Run E2E tests for Iris flows
- Verify no console errors on settings page

---

## Implementation Order (Recommended)

1. **Phase 1:** Models (foundation for everything)
2. **Phase 2:** Service (API layer must work before UI)
3. **Phase 3:** Main settings component (core UI)
4. **Phase 4:** Quick toggle component (depends on service)
5. **Phase 5:** Routes & wrappers (wire everything up)
6. **Phase 6:** Integration points (update consumers)
7. **Phase 7:** Tests (validate changes)
8. **Phase 8:** Translations (UX polish)
10. **Phase 9:** Verification (final QA)

---

## Current Frontend Implementation (Reference)

### Models (`src/main/webapp/app/iris/shared/entities/settings/`)

**iris-settings.model.ts:**
```typescript
export enum IrisSettingsType {
    GLOBAL = 'global',
    COURSE = 'course',
    EXERCISE = 'exercise',
}

export abstract class IrisSettings implements BaseEntity {
    id?: number;
    type: IrisSettingsType;
    irisProgrammingExerciseChatSettings?: IrisProgrammingExerciseChatSubSettings;
    irisTextExerciseChatSettings?: IrisTextExerciseChatSubSettings;
    irisLectureChatSettings?: IrisLectureChatSubSettings;
    irisCourseChatSettings?: IrisCourseChatSubSettings;
    irisLectureIngestionSettings?: IrisLectureIngestionSubSettings;
    irisCompetencyGenerationSettings?: IrisCompetencyGenerationSubSettings;
    irisFaqIngestionSettings?: IrisFaqIngestionSubSettings;
    irisTutorSuggestionSettings?: IrisTutorSuggestionSubSettings;
}
```

**iris-sub-settings.model.ts:**
```typescript
export enum IrisSubSettingsType {
    PROGRAMMING_EXERCISE_CHAT, TEXT_EXERCISE_CHAT, COURSE_CHAT,
    LECTURE_INGESTION, LECTURE, COMPETENCY_GENERATION,
    FAQ_INGESTION, TUTOR_SUGGESTION, ALL
}

export abstract class IrisSubSettings implements BaseEntity {
    id?: number;
    type: IrisSubSettingsType;
    enabled = true;
    allowedVariants?: string[];           // Admin-only
    selectedVariant?: string;             // Admin-only
    enabledForCategories?: string[];      // For exercise chats only
    disabledProactiveEvents?: IrisEventType[]; // For programming chat only
    customInstructions?: string;          // For chat features
}
```

### Service (`src/main/webapp/app/iris/manage/settings/shared/iris-settings.service.ts`)

**Current endpoints:**
- `GET api/iris/global-iris-settings`
- `GET api/iris/courses/{courseId}/raw-iris-settings`
- `GET api/iris/courses/{courseId}/iris-settings` (combined with cache)
- `GET api/iris/exercises/{exerciseId}/raw-iris-settings`
- `GET api/iris/exercises/{exerciseId}/iris-settings` (combined with cache)
- `PUT api/iris/admin/global-iris-settings`
- `PUT api/iris/courses/{courseId}/raw-iris-settings`
- `PUT api/iris/exercises/{exerciseId}/raw-iris-settings`
- `GET api/iris/variants/{feature}`

### Components

**Main Settings Component:**
- `iris-settings-update.component.ts/html` - Generic for all 3 levels × 8 features
- `iris-common-sub-settings-update.component.ts/html` - Per-feature settings form

**Wrapper Components:**
- `iris-global-settings-update.component.ts` - `/admin/iris`
- `iris-course-settings-update.component.ts` - `/course-management/:courseId/iris-settings`
- `iris-exercise-settings-update.component.ts` - Exercise routes

**Quick Toggle:**
- `iris-enabled.component.ts/html` - Status indicator with toggle

### Key Features to Remove

1. **Three-tier inheritance** (Global → Course → Exercise)
2. **Eight feature-specific sub-settings**
3. **Category filtering** (enabledForCategories)
4. **Proactive events** (disabledProactiveEvents)
5. **Per-feature variants** (allowedVariants, selectedVariant per feature)
6. **Per-feature rate limits** (rateLimit per sub-setting)
7. **"Combined" vs "raw" settings** (inheritance resolution)
8. **Global and exercise settings UIs**

### Key Features to Keep/Simplify

1. **Course-level settings** (single source of truth)
2. **Enable/disable toggle** (one for entire course)
3. **Custom instructions** (shared across all features)
4. **Variant selection** (one for entire course, admin-only)
5. **Rate limits** (one for entire course, admin-only)
6. **Quick toggle component** (simplified for course-level)
7. **Settings service caching** (course-only)
8. **Permission checks** (instructors vs admins)
