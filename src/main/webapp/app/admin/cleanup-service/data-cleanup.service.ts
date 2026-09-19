import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import dayjs from 'dayjs/esm';
import { convertDateFromClient, convertDateStringFromServer } from 'app/foundation/util/date.utils';

export interface CleanupServiceExecutionRecordDTO {
    executionDate: dayjs.Dayjs;
    jobType: string;
}

/**
 * A count response read dynamically: one number per affected entity type, whatever the server reports. The concrete
 * types below name their entities and are declared as type aliases, so that they stay assignable here while still
 * rejecting a misspelled key (an interface extending this index signature would accept any key).
 *
 * There is deliberately no `totalCount`: no server count DTO has such a field, and declaring one made the dialog list a
 * phantom, untranslated `totalCount` row while the real counts were still loading.
 */
export type CleanupCount = Record<string, number>;

export type OrphanCleanupCountDTO = {
    orphanStudentScore: number;
    orphanTeamScore: number;
    orphanFeedbackForOrphanResults: number;
    orphanLongFeedbackTextForOrphanResults: number;
    orphanTextBlockForOrphanResults: number;
    orphanRating: number;
    orphanResultsWithoutParticipation: number;
    orphanFeedbackMessage: number;
};

export type PlagiarismComparisonCleanupCountDTO = {
    plagiarismComparison: number;
    plagiarismElements: number;
    plagiarismSubmissions: number;
    plagiarismMatches: number;
};

export type NonLatestNonRatedResultsCleanupCountDTO = {
    longFeedbackText: number;
    textBlock: number;
    feedback: number;
};

export type NonLatestRatedResultsCleanupCountDTO = {
    longFeedbackText: number;
    textBlock: number;
    feedback: number;
};

export type SubmissionVersionsCleanupCountDTO = {
    submissionVersions: number;
};

export type OldCoursesCleanupCountDTO = {
    courses: number;
};

export type OldFeedbackCleanupCountDTO = {
    longFeedbackText: number;
    textBlock: number;
    feedback: number;
};

export type NotEnrolledUsersCleanupCountDTO = {
    users: number;
    blockedUsers: number;
};

export type PlagiarismCasesCleanupCountDTO = {
    plagiarismCases: number;
};

/**
 * The effective retention cutoffs of the age-based cleanup operations. Every cutoff comes as the configured period plus
 * the concrete point in time the operation would apply if it ran now, so the admin page can name the affected data
 * instead of vaguely referring to the configuration in the YAML.
 */
export interface CleanupConfiguration {
    gradeRelevantRetentionYears: number;
    gradeRelevantCoursesEndedBefore: dayjs.Dayjs;
    nonGradeRelevantRetentionYears: number;
    nonGradeRelevantCoursesEndedBefore: dayjs.Dayjs;
    resetWarningGracePeriodDays: number;
    coursesWarnedBefore: dayjs.Dayjs;
    oldFeedbackCutoffWeeks: number;
    oldFeedbackCoursesEndedBefore: dayjs.Dayjs;
    oldSubmissionVersionsCutoffWeeks: number;
    oldSubmissionVersionsCoursesEndedBefore: dayjs.Dayjs;
    notEnrolledUsersInactivityMonths: number;
    usersInactiveBefore: dayjs.Dayjs;
    notEnrolledUsersWarningGracePeriodDays: number;
    usersWarnedBefore: dayjs.Dayjs;
}

/** The configuration as it arrives on the wire, with the cutoffs still ISO strings. */
interface CleanupConfigurationResponse extends Omit<CleanupConfiguration, CleanupConfigurationDateField> {
    gradeRelevantCoursesEndedBefore: string;
    nonGradeRelevantCoursesEndedBefore: string;
    coursesWarnedBefore: string;
    oldFeedbackCoursesEndedBefore: string;
    oldSubmissionVersionsCoursesEndedBefore: string;
    usersInactiveBefore: string;
    usersWarnedBefore: string;
}

type CleanupConfigurationDateField =
    | 'gradeRelevantCoursesEndedBefore'
    | 'nonGradeRelevantCoursesEndedBefore'
    | 'coursesWarnedBefore'
    | 'oldFeedbackCoursesEndedBefore'
    | 'oldSubmissionVersionsCoursesEndedBefore'
    | 'usersInactiveBefore'
    | 'usersWarnedBefore';

@Injectable({ providedIn: 'root' })
export class DataCleanupService {
    private readonly adminResourceUrl = 'api/admin/cleanup';
    private http = inject(HttpClient);

    /**
     * Send DELETE request to delete orphaned data.
     * @returns An observable of type HttpResponse<CleanupServiceExecutionRecordDTO>.
     */
    deleteOrphans(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/orphans`, {
            observe: 'response',
        });
    }

    /**
     * Send DELETE request to delete plagiarism comparisons within a specific date range.
     * @param deleteFrom the start date from which plagiarism comparisons should be deleted
     * @param deleteTo the end date until which plagiarism comparisons should be deleted
     */
    deletePlagiarismComparisons(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        const deleteFromString = convertDateFromClient(deleteFrom)!;
        const deleteToString = convertDateFromClient(deleteTo)!;
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/plagiarism-comparisons`, {
            params: { deleteFrom: deleteFromString, deleteTo: deleteToString },
            observe: 'response',
        });
    }

    /**
     * Send DELETE request to delete non-rated results within a specific date range.
     * @param deleteFrom the start date from which non-rated results should be deleted
     * @param deleteTo the end date until which non-rated results should be deleted
     */
    deleteNonRatedResults(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        const deleteFromString = convertDateFromClient(deleteFrom)!;
        const deleteToString = convertDateFromClient(deleteTo)!;
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/non-rated-results`, {
            params: { deleteFrom: deleteFromString, deleteTo: deleteToString },
            observe: 'response',
        });
    }

    /**
     * Send DELETE request to delete old rated results within a specific date range.
     * @param deleteFrom the start date from which old rated results should be deleted
     * @param deleteTo the end date until which old rated results should be deleted
     */
    deleteOldRatedResults(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        const deleteFromString = convertDateFromClient(deleteFrom)!;
        const deleteToString = convertDateFromClient(deleteTo)!;
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/old-rated-results`, {
            params: { deleteFrom: deleteFromString, deleteTo: deleteToString },
            observe: 'response',
        });
    }

    /**
     * Send DELETE request to delete old submission versions within a specific date range.
     * @param deleteFrom the start date from which old rated results should be deleted
     * @param deleteTo the end date until which old rated results should be deleted
     */
    deleteOldSubmissionVersions(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        const deleteFromString = convertDateFromClient(deleteFrom)!;
        const deleteToString = convertDateFromClient(deleteTo)!;
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/old-submission-versions`, {
            params: { deleteFrom: deleteFromString, deleteTo: deleteToString },
            observe: 'response',
        });
    }

    /**
     * Send POST request to archive old courses due for a student-data reset and warn their instructors.
     */
    warnOldCoursesReset(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.post<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/old-courses/warn`, undefined, { observe: 'response' });
    }

    /**
     * Send DELETE request to reset the student data of old courses that are past the reset grace period.
     */
    resetOldCourses(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/old-courses/reset`, { observe: 'response' });
    }

    /**
     * Send DELETE request to delete the feedback of non-latest results of old courses.
     */
    deleteOldFeedback(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/old-feedback`, { observe: 'response' });
    }

    /**
     * Send DELETE request to delete the submission versions of old courses.
     */
    deleteOldCourseSubmissionVersions(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/old-course-submission-versions`, { observe: 'response' });
    }

    /**
     * Send POST request to warn not-enrolled, inactive users that their account will be deleted after the grace period.
     */
    warnNotEnrolledUsers(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.post<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/not-enrolled-users/warn`, undefined, { observe: 'response' });
    }

    /**
     * Send DELETE request to permanently delete warned users who are enrolled in no course, past the deletion grace period, and have no remaining domain references.
     */
    deleteNotEnrolledUsers(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/not-enrolled-users`, { observe: 'response' });
    }

    /**
     * Send DELETE request to delete the plagiarism cases of courses that ended before the grade-relevant retention cutoff.
     */
    deletePlagiarismCases(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.delete<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/plagiarism-cases`, { observe: 'response' });
    }

    /**
     * Send GET request to get the effective retention cutoffs of the age-based operations.
     * The cutoffs arrive as ISO strings and are converted to dayjs here, so callers can format them directly.
     */
    getCleanupConfiguration(): Observable<CleanupConfiguration> {
        return this.http.get<CleanupConfigurationResponse>(`${this.adminResourceUrl}/configuration`).pipe(
            map((configuration) => ({
                gradeRelevantRetentionYears: configuration.gradeRelevantRetentionYears,
                gradeRelevantCoursesEndedBefore: cutoff(configuration.gradeRelevantCoursesEndedBefore),
                nonGradeRelevantRetentionYears: configuration.nonGradeRelevantRetentionYears,
                nonGradeRelevantCoursesEndedBefore: cutoff(configuration.nonGradeRelevantCoursesEndedBefore),
                resetWarningGracePeriodDays: configuration.resetWarningGracePeriodDays,
                coursesWarnedBefore: cutoff(configuration.coursesWarnedBefore),
                oldFeedbackCutoffWeeks: configuration.oldFeedbackCutoffWeeks,
                oldFeedbackCoursesEndedBefore: cutoff(configuration.oldFeedbackCoursesEndedBefore),
                oldSubmissionVersionsCutoffWeeks: configuration.oldSubmissionVersionsCutoffWeeks,
                oldSubmissionVersionsCoursesEndedBefore: cutoff(configuration.oldSubmissionVersionsCoursesEndedBefore),
                notEnrolledUsersInactivityMonths: configuration.notEnrolledUsersInactivityMonths,
                usersInactiveBefore: cutoff(configuration.usersInactiveBefore),
                notEnrolledUsersWarningGracePeriodDays: configuration.notEnrolledUsersWarningGracePeriodDays,
                usersWarnedBefore: cutoff(configuration.usersWarnedBefore),
            })),
        );
    }

    /**
     * Send GET request to get the last executions.
     * @returns An observable of type HttpResponse<CleanupServiceExecutionRecordDTO[]>.
     */
    getLastExecutions(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO[]>> {
        return this.http.get<CleanupServiceExecutionRecordDTO[]>(`${this.adminResourceUrl}/last-executions`, {
            observe: 'response',
        });
    }

    /**
     * Send GET request to count orphaned data.
     * @returns An observable of type HttpResponse<OrphanCleanupCountDTO>.
     */
    countOrphans(): Observable<HttpResponse<OrphanCleanupCountDTO>> {
        return this.http.get<OrphanCleanupCountDTO>(`${this.adminResourceUrl}/orphans/count`, {
            observe: 'response',
        });
    }

    /**
     * Send GET request to count plagiarism comparisons within a specific date range.
     * @param deleteFrom the start date for counting
     * @param deleteTo the end date for counting
     * @returns An observable of type HttpResponse<PlagiarismComparisonCleanupCountDTO>.
     */
    countPlagiarismComparisons(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<HttpResponse<PlagiarismComparisonCleanupCountDTO>> {
        const deleteFromString = convertDateFromClient(deleteFrom)!;
        const deleteToString = convertDateFromClient(deleteTo)!;
        return this.http.get<PlagiarismComparisonCleanupCountDTO>(`${this.adminResourceUrl}/plagiarism-comparisons/count`, {
            params: { deleteFrom: deleteFromString, deleteTo: deleteToString },
            observe: 'response',
        });
    }

    /**
     * Send GET request to count non-rated results within a specific date range.
     * @param deleteFrom the start date for counting
     * @param deleteTo the end date for counting
     * @returns An observable of type HttpResponse<NonLatestNonRatedResultsCleanupCountDTO>.
     */
    countNonRatedResults(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<HttpResponse<NonLatestNonRatedResultsCleanupCountDTO>> {
        const deleteFromString = convertDateFromClient(deleteFrom)!;
        const deleteToString = convertDateFromClient(deleteTo)!;
        return this.http.get<NonLatestNonRatedResultsCleanupCountDTO>(`${this.adminResourceUrl}/non-rated-results/count`, {
            params: { deleteFrom: deleteFromString, deleteTo: deleteToString },
            observe: 'response',
        });
    }

    /**
     * Send GET request to count old rated results within a specific date range.
     * @param deleteFrom the start date for counting
     * @param deleteTo the end date for counting
     * @returns An observable of type HttpResponse<NonLatestRatedResultsCleanupCountDTO>.
     */
    countOldRatedResults(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<HttpResponse<NonLatestRatedResultsCleanupCountDTO>> {
        const deleteFromString = convertDateFromClient(deleteFrom)!;
        const deleteToString = convertDateFromClient(deleteTo)!;
        return this.http.get<NonLatestRatedResultsCleanupCountDTO>(`${this.adminResourceUrl}/old-rated-results/count`, {
            params: { deleteFrom: deleteFromString, deleteTo: deleteToString },
            observe: 'response',
        });
    }

    /**
     * Send GET request to count old submission versions within a specific date range.
     * @param deleteFrom the start date for counting
     * @param deleteTo the end date for counting
     * @returns An observable of type HttpResponse<SubmissionVersionsCleanupCountDTO>.
     */
    countOldSubmissionVersions(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<HttpResponse<SubmissionVersionsCleanupCountDTO>> {
        const deleteFromString = convertDateFromClient(deleteFrom)!;
        const deleteToString = convertDateFromClient(deleteTo)!;
        return this.http.get<SubmissionVersionsCleanupCountDTO>(`${this.adminResourceUrl}/old-submission-versions/count`, {
            params: { deleteFrom: deleteFromString, deleteTo: deleteToString },
            observe: 'response',
        });
    }

    /**
     * Send GET request to count old courses due for a student-data reset warning.
     */
    countOldCoursesResetWarning(): Observable<HttpResponse<OldCoursesCleanupCountDTO>> {
        return this.http.get<OldCoursesCleanupCountDTO>(`${this.adminResourceUrl}/old-courses/warn/count`, { observe: 'response' });
    }

    /**
     * Send GET request to count old courses due for a student-data reset.
     */
    countOldCoursesReset(): Observable<HttpResponse<OldCoursesCleanupCountDTO>> {
        return this.http.get<OldCoursesCleanupCountDTO>(`${this.adminResourceUrl}/old-courses/reset/count`, { observe: 'response' });
    }

    /**
     * Send GET request to count the feedback of non-latest results of old courses.
     */
    countOldFeedback(): Observable<HttpResponse<OldFeedbackCleanupCountDTO>> {
        return this.http.get<OldFeedbackCleanupCountDTO>(`${this.adminResourceUrl}/old-feedback/count`, { observe: 'response' });
    }

    /**
     * Send GET request to count the submission versions of old courses.
     */
    countOldCourseSubmissionVersions(): Observable<HttpResponse<SubmissionVersionsCleanupCountDTO>> {
        return this.http.get<SubmissionVersionsCleanupCountDTO>(`${this.adminResourceUrl}/old-course-submission-versions/count`, { observe: 'response' });
    }

    /**
     * Send GET request to count the not-enrolled, inactive users that would be warned about an upcoming deletion.
     */
    countNotEnrolledUsersWarning(): Observable<HttpResponse<NotEnrolledUsersCleanupCountDTO>> {
        return this.http.get<NotEnrolledUsersCleanupCountDTO>(`${this.adminResourceUrl}/not-enrolled-users/warn/count`, { observe: 'response' });
    }

    /**
     * Send GET request to count the warned users that would be permanently deleted or blocked by remaining references.
     */
    countNotEnrolledUsers(): Observable<HttpResponse<NotEnrolledUsersCleanupCountDTO>> {
        return this.http.get<NotEnrolledUsersCleanupCountDTO>(`${this.adminResourceUrl}/not-enrolled-users/count`, { observe: 'response' });
    }

    /**
     * Send GET request to count the plagiarism cases of courses that ended before the grade-relevant retention cutoff.
     */
    countPlagiarismCases(): Observable<HttpResponse<PlagiarismCasesCleanupCountDTO>> {
        return this.http.get<PlagiarismCasesCleanupCountDTO>(`${this.adminResourceUrl}/plagiarism-cases/count`, { observe: 'response' });
    }
}

/** An ISO-8601 instant as the server serializes a `ZonedDateTime`, captured down to its calendar date. */
const ISO_INSTANT = /^(\d{4})-(\d{2})-(\d{2})T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/;

/**
 * Parses a cutoff sent by the server, rejecting anything that is not exactly one calendar instant.
 *
 * Both lenient outcomes of a bare `dayjs()` are wrong here, on the one page whose purpose is stating exact dates:
 * `dayjs(undefined)` yields *now*, and `dayjs('2024-02-30T00:00:00Z')` yields 1 March with `isValid()` true, because
 * dayjs normalizes an out-of-range day instead of refusing it. Both would read as a plausible but wrong cutoff, so the
 * shape and the calendar date are checked before the value is accepted.
 */
function cutoff(value: string): dayjs.Dayjs {
    const match = ISO_INSTANT.exec(value ?? '');
    const parsed = match && convertDateStringFromServer(value);
    if (!parsed?.isValid() || !isRealCalendarDate(match![1], match![2], match![3])) {
        throw new Error(`The cleanup configuration has no usable cutoff: received ${String(value)}`);
    }
    return parsed;
}

/** Whether the year/month/day triple survives a UTC round trip, i.e. is a date that exists. */
function isRealCalendarDate(year: string, month: string, day: string): boolean {
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    return date.getUTCFullYear() === Number(year) && date.getUTCMonth() === Number(month) - 1 && date.getUTCDate() === Number(day);
}
