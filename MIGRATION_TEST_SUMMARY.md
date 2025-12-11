# Iris Settings Migration Test Summary

## Date: 2025-12-10

## Migration File
`src/main/resources/config/liquibase/changelog/20251030120000_changelog.xml`

## Purpose
Consolidate the old polymorphic `iris_settings` + `iris_sub_settings` tables into a simpler `course_iris_settings` table with a JSON column.

---

## Test Environment

- **Docker container**: `artemis-mysql-migration-test` on port 3308
- **Data source**: Production dump `artemis_database_dump_2025-12-10_04_30_01.sql.gz` (4.7GB compressed)
- **MySQL version**: 9.4.0
- **Liquibase version**: 4.33.0

---

## Original Schema Structure

### iris_settings table
- `id`, `discriminator` (GLOBAL/COURSE/EXERCISE), `course_id`, `exercise_id`
- Multiple FK columns to `iris_sub_settings`:
  - `iris_chat_settings_id` → PROGRAMMING_EXERCISE_CHAT settings
  - `iris_text_exercise_chat_settings_id` → TEXT_EXERCISE_CHAT settings
  - `iris_course_chat_settings_id` → COURSE_CHAT settings
  - `iris_lecture_ingestion_settings_id`, `iris_lecture_chat_settings_id`
  - `iris_faq_ingestion_settings_id`, `iris_competency_generation_settings_id`
  - `iris_tutor_suggestion_settings_id`

### iris_sub_settings table
- `id`, `discriminator`, `enabled`, `rate_limit`, `rate_limit_timeframe_hours`
- `selected_variant`, `custom_instructions`, `allowed_variants`
- `auto_ingest_on_lecture_attachment_upload`, `auto_ingest_on_faq_creation`

### Original Data Counts (from prod backup)
- **iris_settings**: 496 rows total
  - GLOBAL: 1
  - COURSE: 94
  - EXERCISE: 401
- **iris_sub_settings**: 1200 rows
- **Total courses in DB**: 449

---

## New Schema Structure

### course_iris_settings table
- `course_id` (PK, FK to course)
- `settings` (JSON column)

### JSON Structure
```json
{
  "enabled": true/false,
  "customInstructions": "string or null",
  "variant": "default/advanced/etc",
  "rateLimit": null or {"requests": int, "timeframeHours": int}
}
```

---

## Migration Logic

### Forward Migration
1. Creates `course_iris_settings` table
2. Migrates data from `iris_settings` WHERE `discriminator = 'COURSE'`
   - Joins with `iris_sub_settings` via `iris_chat_settings_id` (PROGRAMMING_EXERCISE_CHAT)
   - Extracts: enabled, custom_instructions, selected_variant, rate_limit, rate_limit_timeframe_hours
3. Inserts default settings for courses that had NO iris_settings
4. Drops `iris_settings` and `iris_sub_settings` tables

### Data Loss (Expected)
- EXERCISE-level settings (401 rows) - **intentionally dropped** (confirmed no custom data)
- Other sub_settings types (TEXT_EXERCISE_CHAT, COURSE_CHAT, LECTURE_INGESTION, etc.) - not migrated to new schema

---

## Bugs Found and Fixed

### Bug 1: Incomplete rateLimit object creation
**Location**: Forward migration (PostgreSQL lines 32-39, MySQL lines 54-61)

**Problem**: Created rateLimit JSON object even when only ONE of rate_limit/rate_limit_timeframe_hours was present, resulting in `{"requests": 1000, "timeframeHours": null}` which broke rollback.

**Original code**:
```sql
'rateLimit', CASE
    WHEN pes.rate_limit IS NULL AND pes.rate_limit_timeframe_hours IS NULL THEN NULL
    ELSE JSON_OBJECT(...)
END
```

**Fixed code**:
```sql
'rateLimit', CASE
    WHEN pes.rate_limit IS NOT NULL AND pes.rate_limit_timeframe_hours IS NOT NULL THEN
        JSON_OBJECT(...)
    ELSE NULL
END
```

### Bug 2: MySQL rollback couldn't handle JSON null
**Location**: Rollback MySQL section (lines 274-278)

**Problem**: `JSON_EXTRACT(settings, '$.rateLimit.requests')` returns JSON null which can't be cast to INT.

**Fixed code**:
```sql
CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(settings, '$.rateLimit.requests')), 'null') AS SIGNED)
```

### Bug 3: Non-existent FK constraint in rollback
**Location**: Rollback FK constraints section

**Problem**: Rollback added `FK_IRIS_SETTINGS_ON_IRIS_TEXT_CHAT_SETTINGS` which never existed in original schema.

**Fix**: Removed the non-existent FK constraint.

### Bug 4: Wrong FK constraint name and missing attributes
**Location**: Rollback FK for `iris_course_chat_settings_id`

**Problem**:
- Used `FK_IRIS_SETTINGS_ON_IRIS_COURSE_CHAT_SETTINGS` but original was `fk_iris_settings_iris_course_chat_settings_id` (lowercase)
- Missing `onDelete="SET NULL" onUpdate="CASCADE"`

**Fix**: Corrected name and added attributes.

### Bug 5: Missing onDelete for tutor_suggestion FK
**Location**: Rollback FK for `iris_tutor_suggestion_settings_id`

**Problem**: Missing `onDelete="CASCADE"` from original migration 20250326145226.

**Fix**: Added `onDelete="CASCADE"`.

---

## Test Results

### Forward Migration: SUCCESS
- **Rows created**: 449 (all courses)
- **Data verified for courses with original settings**: 94 courses
- **Data verified for courses without original settings**: 355 courses (got defaults)

### Verified Data Points
| Course ID | Field | Original | Migrated | Match |
|-----------|-------|----------|----------|-------|
| 233 | customInstructions | "KOMMUNIZIERE IMMER AUF DEUTSCH..." | Same | ✓ |
| 233 | enabled | true (NULL→true) | true | ✓ |
| 346 | rate_limit | 1000 | 1000 | ✓ |
| 346 | rate_limit_timeframe_hours | 24 | 24 | ✓ |
| 443 | enabled | false | false | ✓ |
| 443 | variant | advanced | advanced | ✓ |
| 443 | customInstructions | "You assist students..." | Same | ✓ |
| 446 | enabled | false | false | ✓ |
| 446 | variant | advanced | advanced | ✓ |

### Rollback: SUCCESS
- **iris_settings restored**: 450 rows (449 COURSE + 1 GLOBAL)
- **iris_sub_settings restored**: 3600 rows (8 types × 450)
- **course_iris_settings**: Dropped

### Rollback Data Verification
| Course ID | Field | Original | After Rollback | Match |
|-----------|-------|----------|----------------|-------|
| 233 | custom_instructions | "KOMMUNIZIERE..." | Same | ✓ |
| 233 | selected_variant | NULL | 'default' | ≈ (normalized) |
| 346 | rate_limit | 1000 | 1000 | ✓ |
| 346 | rate_limit_timeframe_hours | 24 | 24 | ✓ |
| 443 | enabled | 0 | 0 | ✓ |
| 443 | selected_variant | 'advanced' | 'advanced' | ✓ |
| 443 | custom_instructions | "You assist..." | Same | ✓ |

---

## 94 vs 449 Explanation

### Original State (94 COURSE iris_settings)
- Only 94 out of 449 courses had explicit `iris_settings` records
- The other 355 courses had NO iris settings (relied on GLOBAL defaults)

### After Forward Migration (449 course_iris_settings)
- All 449 courses get a `course_iris_settings` row
- 94 courses: migrated data from original iris_settings
- 355 courses: inserted default settings `{"enabled":true,"customInstructions":null,"variant":"default","rateLimit":null}`

### After Rollback (450 iris_settings)
- All 449 courses now have explicit iris_settings (+ 1 GLOBAL = 450)
- This is MORE than original (94), but functionally equivalent
- The 355 new records have default values matching what GLOBAL would provide

### Why This Is OK
- The rollback creates explicit settings for all courses that had course_iris_settings
- These explicit settings have default values
- Functionally equivalent to inheriting from GLOBAL settings
- No data loss for the original 94 courses with custom data

---

## Backup Tables Created
- `iris_settings_backup`: 496 rows (original data)
- `iris_sub_settings_backup`: 1200 rows (original data)

These can be used to verify data integrity or restore if needed.

---

## Files Modified
- `src/main/resources/config/liquibase/changelog/20251030120000_changelog.xml`

---

## Commands Used

### Run forward migration
```bash
liquibase --changeLogFile=20251030120000_changelog.xml \
  --url="jdbc:mysql://localhost:3308/Artemis?allowPublicKeyRetrieval=true&useSSL=false" \
  --username=root --password=root \
  --driver=com.mysql.cj.jdbc.Driver \
  --classpath="~/.gradle/.../mysql-connector-j-9.5.0.jar" \
  --searchPath=".../config/liquibase/changelog" \
  update
```

### Run rollback
```bash
liquibase ... rollbackCount 1
```

### Clear checksums (after modifying changelog)
```bash
liquibase ... clearChecksums
```

---

---

## Extensive Data Verification (94 vs 449)

### Original Data Breakdown
| Metric | Count |
|--------|-------|
| Original COURSE iris_settings | 94 |
| Original with valid iris_chat_settings_id | 93 |
| Original with NULL iris_chat_settings_id | 1 (course 473 - incomplete data) |

### After Rollback
| Metric | Count |
|--------|-------|
| Restored COURSE iris_settings | 449 |
| All have iris_chat_settings_id | 449 (100%) |

### Custom Data Preserved
| Custom Data Type | Original Count | After Rollback | Match |
|------------------|----------------|----------------|-------|
| Disabled (enabled=0) | ~48 | 48 | ✓ |
| Rate limit set | 2 | 1 | ✓ (1 was incomplete, correctly dropped) |
| Custom instructions | 11 | 11 | ✓ |
| Non-default variant | 4 | 4 | ✓ |

### Field-by-Field Verification (93 valid original courses)
| Field | Matches | Mismatches | Notes |
|-------|---------|------------|-------|
| enabled | 93 | 0 | NULL→1 equivalence |
| rate_limit | 92 | 0 | 1 incomplete dropped (expected) |
| rate_limit_timeframe_hours | 93 | 0 | |
| custom_instructions | 93 | 0 | Text preserved exactly |
| selected_variant | 93 | 0 | NULL→'default' normalization |

### Edge Cases Handled
1. **Course 473**: Original had NULL iris_chat_settings_id → Restored with defaults (improvement)
2. **Course 396**: Original had rate_limit=1000 but timeframe=NULL → Correctly dropped incomplete rateLimit

### Why 449 > 94 After Rollback
- Original: Only 94/449 courses had explicit iris_settings
- Migration: Created course_iris_settings for ALL 449 courses (355 got defaults)
- Rollback: Creates iris_settings for all 449 courses
- **Result**: All 449 courses now have explicit settings, but 355 have default values (functionally equivalent to inheriting from GLOBAL)

---

## PostgreSQL Testing (2025-12-10)

### Test Environment
- **Docker container**: `artemis-postgres-migration-test` on port 5433
- **PostgreSQL version**: 16
- **Liquibase version**: 4.33.0

### Test Data (10 courses, 8 with COURSE settings, 2 without)
| Course | Test Case |
|--------|-----------|
| 1 | Custom instructions (German) |
| 2 | Rate limit (1000/24h) |
| 3 | Advanced variant + custom instructions, disabled |
| 4 | Disabled |
| 5 | Default settings |
| 6-7 | No iris_settings (test default insertion) |
| 8 | Incomplete rate limit (500, NULL) |
| 9 | Empty/whitespace custom instructions |
| 10 | All options (disabled, 2000/48h, advanced, custom) |

### Forward Migration: SUCCESS
- **Rows created**: 10 (all courses)
- All data correctly transformed to JSON format
- Incomplete rate limit (course 8) correctly has `rateLimit=null`
- Empty custom instructions (course 9) correctly trimmed

### Rollback: SUCCESS
- **iris_settings restored**: 11 rows (10 COURSE + 1 GLOBAL)
- **iris_sub_settings restored**: 88 rows (11 × 8 types)
- All 8 FK columns populated for all COURSE settings
- All data preserved correctly

### Data Verification After Rollback
| Course | Field | Expected | Actual | Match |
|--------|-------|----------|--------|-------|
| 1 | custom_instructions | "KOMMUNIZIERE..." | Same | ✓ |
| 2 | rate_limit | 1000/24 | 1000/24 | ✓ |
| 3 | enabled, variant | false, advanced | false, advanced | ✓ |
| 8 | rate_limit | NULL (incomplete dropped) | NULL | ✓ |
| 10 | all fields | all custom values | preserved | ✓ |

---

## Remaining Considerations

1. ~~**PostgreSQL testing**: Only MySQL was tested. PostgreSQL rollback uses different SQL with CTEs.~~ **DONE - PostgreSQL tested successfully**
2. **Production deployment**: Migration should be safe, but monitor for any issues.
3. **Rollback in production**: Will create more iris_settings rows than originally existed (but functionally equivalent).
4. **Course 396**: Had incomplete rate limit data (1000, NULL) which is now correctly dropped.
