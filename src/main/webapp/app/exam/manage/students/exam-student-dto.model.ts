import dayjs from 'dayjs/esm';
import { SearchTermPageableSearch } from 'app/foundation/pagination/pageable-table';

export interface ExamStudentSearch extends SearchTermPageableSearch {
    filterProp?: string;
}

export type ExamProgress = 'examMissing' | 'notStarted' | 'started' | 'submitted' | 'inconsistent';

/**
 * Merged row shape for the exam-students table. Mirrors the server-side {@code ExamStudentDTO}:
 * an {@link ExamUser} (registration + room/seat + attendance checks) combined with the
 * matching {@link StudentExam} (working time + submission progress). Fields sourced from the
 * student-exam side are nullable because a registered student may not yet have a generated student exam.
 */
export interface ExamStudentDTO {
    // ExamUser fields
    id: number;
    userId?: number;
    login?: string;
    name?: string;
    email?: string;
    visibleRegistrationNumber?: string;
    studentImagePath?: string;
    plannedRoom?: string;
    actualRoom?: string;
    plannedSeat?: string;
    actualSeat?: string;
    didCheckImage?: boolean;
    didCheckName?: boolean;
    didCheckLogin?: boolean;
    didCheckRegistrationNumber?: boolean;
    signingImagePath?: string;
    didExamUserAttendExam?: boolean;
    // StudentExam fields (undefined when no student exam has been generated)
    studentExamId?: number;
    workingTime?: number;
    started?: boolean;
    submitted?: boolean;
    startedDate?: dayjs.Dayjs;
    submissionDate?: dayjs.Dayjs;
    numberOfExamSessions?: number;
    progress?: ExamProgress;
}
