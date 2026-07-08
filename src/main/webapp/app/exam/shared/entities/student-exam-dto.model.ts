import dayjs from 'dayjs/esm';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

/**
 * Minimal user projection returned alongside a {@link StudentExamDTO}.
 * Matches the server-side `de.tum.cit.aet.artemis.core.dto.UserNameDTO` record: no email, no groups, no other
 * account fields.
 */
export interface UserNameDTO {
    id?: number;
    login?: string;
    name?: string;
}

/**
 * Matches the server-side `CourseForStudentExamDTO` record: only the fields `AccountService.setAccessRightsForCourse`
 * needs to compute the `isAtLeast*` client-only flags.
 */
export interface CourseForStudentExamDTO {
    id: number;
    instructorGroupName?: string;
    editorGroupName?: string;
    teachingAssistantGroupName?: string;
}

/**
 * Matches the server-side `ExamForStudentExamDTO` record: the subset of `Exam` fields needed by
 * `processStudentExam`/`isWithinWorkingTime`/`calculateUsedWorkingTime`. Notably absent: `startDate`, `gracePeriod`,
 * `exerciseGroups`, etc. — callers must not read those off this projection.
 */
export interface ExamForStudentExamDTO {
    id: number;
    title?: string;
    testExam: boolean;
    workingTime: number;
    course?: CourseForStudentExamDTO;
}

/**
 * Matches the server-side `StudentExamDTO` record shared by all 7 migrated `StudentExamResource` endpoints
 * (student-exams list, working-time PATCH, test-exams-per-user, test-runs list/POST, toggle-to-submitted/unsubmitted).
 * Every endpoint returns this same shape; which of `user`/`exam` are populated depends on the endpoint (see the
 * call sites in `student-exam.service.ts` / `exam-management.service.ts` / `exam-participation.service.ts`).
 * Never present: `exercises`, `examSessions`, `numberOfExamSessions` — none of the in-scope endpoints serialize
 * lazy collections.
 */
export interface StudentExamDTO {
    id: number;
    workingTime?: number;
    started?: boolean;
    startedDate?: dayjs.Dayjs;
    submitted?: boolean;
    submissionDate?: dayjs.Dayjs;
    testRun: boolean;
    user?: UserNameDTO;
    exam?: ExamForStudentExamDTO;
}

/**
 * Some pre-existing client state (e.g. `CourseExamsComponent.studentExams`) mixes items fetched from the migrated
 * `test-exams-per-user` DTO endpoint with full {@link StudentExam} entities coming from other, not-yet-migrated
 * endpoints (`currentlyLoadedStudentExam`/live exam participation). This union models that reality honestly instead
 * of widening back to `StudentExam` (which would silently permit reading fields the DTO endpoint never returns).
 */
export type StudentExamOrDTO = StudentExam | StudentExamDTO;
