import { Service, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import dayjs from 'dayjs/esm';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { AdminCleanupApi } from 'app/openapi/api/admin-cleanup-api';
import { CleanupConfiguration as CleanupConfigurationResponse } from 'app/openapi/model/cleanup-configuration';
import { CleanupServiceExecutionRecord } from 'app/openapi/model/cleanup-service-execution-record';
import { OrphanCleanupCount } from 'app/openapi/model/orphan-cleanup-count';
import { PlagiarismComparisonCleanupCount } from 'app/openapi/model/plagiarism-comparison-cleanup-count';
import { NonLatestNonRatedResultsCleanupCount } from 'app/openapi/model/non-latest-non-rated-results-cleanup-count';
import { NonLatestRatedResultsCleanupCount } from 'app/openapi/model/non-latest-rated-results-cleanup-count';
import { SubmissionVersionsCleanupCount } from 'app/openapi/model/submission-versions-cleanup-count';
import { OldCoursesCleanupCount } from 'app/openapi/model/old-courses-cleanup-count';
import { OldFeedbackCleanupCount } from 'app/openapi/model/old-feedback-cleanup-count';
import { NotEnrolledUsersCleanupCount } from 'app/openapi/model/not-enrolled-users-cleanup-count';
import { PlagiarismCasesCleanupCount } from 'app/openapi/model/plagiarism-cases-cleanup-count';

export interface CleanupServiceExecutionRecordDTO {
    executionDate?: dayjs.Dayjs;
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

type Indexable<T> = { [K in keyof T]: T[K] };

export type OrphanCleanupCountDTO = Indexable<OrphanCleanupCount>;
export type PlagiarismComparisonCleanupCountDTO = Indexable<PlagiarismComparisonCleanupCount>;
export type NonLatestNonRatedResultsCleanupCountDTO = Indexable<NonLatestNonRatedResultsCleanupCount>;
export type NonLatestRatedResultsCleanupCountDTO = Indexable<NonLatestRatedResultsCleanupCount>;
export type SubmissionVersionsCleanupCountDTO = Indexable<SubmissionVersionsCleanupCount>;
export type OldCoursesCleanupCountDTO = Indexable<OldCoursesCleanupCount>;
export type OldFeedbackCleanupCountDTO = Indexable<OldFeedbackCleanupCount>;
export type NotEnrolledUsersCleanupCountDTO = Indexable<NotEnrolledUsersCleanupCount>;
export type PlagiarismCasesCleanupCountDTO = Indexable<PlagiarismCasesCleanupCount>;

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

@Service()
export class DataCleanupService {
    private readonly api = inject(AdminCleanupApi);

    /**
     * Send DELETE request to delete orphaned data.
     */
    deleteOrphans(): Observable<CleanupServiceExecutionRecordDTO> {
        return this.api.deleteOrphans().pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to delete plagiarism comparisons within a specific date range.
     * @param deleteFrom the start date from which plagiarism comparisons should be deleted
     * @param deleteTo the end date until which plagiarism comparisons should be deleted
     */
    deletePlagiarismComparisons(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<CleanupServiceExecutionRecordDTO> {
        return defer(() => this.api.deletePlagiarismComparisons(this.toIsoDate(deleteFrom), this.toIsoDate(deleteTo))).pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to delete non-rated results within a specific date range.
     * @param deleteFrom the start date from which non-rated results should be deleted
     * @param deleteTo the end date until which non-rated results should be deleted
     */
    deleteNonRatedResults(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<CleanupServiceExecutionRecordDTO> {
        return defer(() => this.api.deleteNonRatedResults(this.toIsoDate(deleteFrom), this.toIsoDate(deleteTo))).pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to delete old rated results within a specific date range.
     * @param deleteFrom the start date from which old rated results should be deleted
     * @param deleteTo the end date until which old rated results should be deleted
     */
    deleteOldRatedResults(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<CleanupServiceExecutionRecordDTO> {
        return defer(() => this.api.deleteOldRatedResults(this.toIsoDate(deleteFrom), this.toIsoDate(deleteTo))).pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to delete old submission versions within a specific date range.
     * @param deleteFrom the start date from which old submission versions should be deleted
     * @param deleteTo the end date until which old submission versions should be deleted
     */
    deleteOldSubmissionVersions(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<CleanupServiceExecutionRecordDTO> {
        return defer(() => this.api.deleteOldSubmissionVersions(this.toIsoDate(deleteFrom), this.toIsoDate(deleteTo))).pipe(map(toExecutionRecord));
    }

    /**
     * Send POST request to archive old courses due for a student-data reset and warn their instructors.
     */
    warnOldCoursesReset(): Observable<CleanupServiceExecutionRecordDTO> {
        return this.api.warnOldCoursesReset().pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to reset the student data of old courses that are past the reset grace period.
     */
    resetOldCourses(): Observable<CleanupServiceExecutionRecordDTO> {
        return this.api.resetOldCourses().pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to delete the feedback of non-latest results of old courses.
     */
    deleteOldFeedback(): Observable<CleanupServiceExecutionRecordDTO> {
        return this.api.deleteOldFeedback().pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to delete the submission versions of old courses.
     */
    deleteOldCourseSubmissionVersions(): Observable<CleanupServiceExecutionRecordDTO> {
        return this.api.deleteOldCourseSubmissionVersions().pipe(map(toExecutionRecord));
    }

    /**
     * Send POST request to warn not-enrolled, inactive users that their account will be deleted after the grace period.
     */
    warnNotEnrolledUsers(): Observable<CleanupServiceExecutionRecordDTO> {
        return this.api.warnNotEnrolledUsers().pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to permanently delete warned users who are enrolled in no course, past the deletion grace period, and have no remaining domain references.
     */
    deleteNotEnrolledUsers(): Observable<CleanupServiceExecutionRecordDTO> {
        return this.api.deleteNotEnrolledUsers().pipe(map(toExecutionRecord));
    }

    /**
     * Send DELETE request to delete the plagiarism cases of courses that ended before the grade-relevant retention cutoff.
     */
    deletePlagiarismCases(): Observable<CleanupServiceExecutionRecordDTO> {
        return this.api.deletePlagiarismCases().pipe(map(toExecutionRecord));
    }

    /**
     * Send GET request to get the effective retention cutoffs of the age-based operations.
     * The cutoffs arrive as ISO strings and are converted to dayjs here, so callers can format them directly.
     */
    getCleanupConfiguration(): Observable<CleanupConfiguration> {
        return this.api.getCleanupConfiguration().pipe(
            map((configuration: CleanupConfigurationResponse) => ({
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
     */
    getLastExecutions(): Observable<CleanupServiceExecutionRecordDTO[]> {
        return this.api.getLastExecutions().pipe(map((records) => records.map(toExecutionRecord)));
    }

    /**
     * Send GET request to count orphaned data.
     */
    countOrphans(): Observable<OrphanCleanupCountDTO> {
        return this.api.countOrphans();
    }

    /**
     * Send GET request to count plagiarism comparisons within a specific date range.
     * @param deleteFrom the start date for counting
     * @param deleteTo the end date for counting
     */
    countPlagiarismComparisons(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<PlagiarismComparisonCleanupCountDTO> {
        return defer(() => this.api.countPlagiarismComparisons(this.toIsoDate(deleteFrom), this.toIsoDate(deleteTo)));
    }

    /**
     * Send GET request to count non-rated results within a specific date range.
     * @param deleteFrom the start date for counting
     * @param deleteTo the end date for counting
     */
    countNonRatedResults(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<NonLatestNonRatedResultsCleanupCountDTO> {
        return defer(() => this.api.countNonRatedResults(this.toIsoDate(deleteFrom), this.toIsoDate(deleteTo)));
    }

    /**
     * Send GET request to count old rated results within a specific date range.
     * @param deleteFrom the start date for counting
     * @param deleteTo the end date for counting
     */
    countOldRatedResults(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<NonLatestRatedResultsCleanupCountDTO> {
        return defer(() => this.api.countOldRatedResults(this.toIsoDate(deleteFrom), this.toIsoDate(deleteTo)));
    }

    /**
     * Send GET request to count old submission versions within a specific date range.
     * @param deleteFrom the start date for counting
     * @param deleteTo the end date for counting
     */
    countOldSubmissionVersions(deleteFrom: dayjs.Dayjs, deleteTo: dayjs.Dayjs): Observable<SubmissionVersionsCleanupCountDTO> {
        return defer(() => this.api.countOldSubmissionVersions(this.toIsoDate(deleteFrom), this.toIsoDate(deleteTo)));
    }

    /**
     * Send GET request to count old courses due for a student-data reset warning.
     */
    countOldCoursesResetWarning(): Observable<OldCoursesCleanupCountDTO> {
        return this.api.countOldCoursesResetWarning();
    }

    /**
     * Send GET request to count old courses due for a student-data reset.
     */
    countOldCoursesReset(): Observable<OldCoursesCleanupCountDTO> {
        return this.api.countOldCoursesReset();
    }

    /**
     * Send GET request to count the feedback of non-latest results of old courses.
     */
    countOldFeedback(): Observable<OldFeedbackCleanupCountDTO> {
        return this.api.countOldFeedback();
    }

    /**
     * Send GET request to count the submission versions of old courses.
     */
    countOldCourseSubmissionVersions(): Observable<SubmissionVersionsCleanupCountDTO> {
        return this.api.countOldCourseSubmissionVersions();
    }

    /**
     * Send GET request to count the not-enrolled, inactive users that would be warned about an upcoming deletion.
     */
    countNotEnrolledUsersWarning(): Observable<NotEnrolledUsersCleanupCountDTO> {
        return this.api.countNotEnrolledUsersWarning();
    }

    /**
     * Send GET request to count the warned users that would be permanently deleted or blocked by remaining references.
     */
    countNotEnrolledUsers(): Observable<NotEnrolledUsersCleanupCountDTO> {
        return this.api.countNotEnrolledUsers();
    }

    /**
     * Send GET request to count the plagiarism cases of courses that ended before the grade-relevant retention cutoff.
     */
    countPlagiarismCases(): Observable<PlagiarismCasesCleanupCountDTO> {
        return this.api.countPlagiarismCases();
    }

    private toIsoDate(date: dayjs.Dayjs): string {
        if (!date.isValid()) {
            throw new Error('The cleanup date range contains an invalid date.');
        }
        return date.toISOString();
    }
}

function toExecutionRecord(record: CleanupServiceExecutionRecord): CleanupServiceExecutionRecordDTO {
    return { jobType: record.jobType, executionDate: convertDateStringFromServer(record.executionDate) };
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
function cutoff(value: string | undefined): dayjs.Dayjs {
    const match = ISO_INSTANT.exec(value ?? '');
    const parsed = match && convertDateStringFromServer(value);
    if (!match || !isRealCalendarDate(match[1], match[2], match[3]) || !parsed?.isValid()) {
        throw new Error(`The cleanup configuration has no usable cutoff: received ${String(value)}`);
    }
    return parsed;
}

/** Whether the year/month/day triple survives a UTC round trip, i.e. is a date that exists. */
function isRealCalendarDate(year: string, month: string, day: string): boolean {
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    return date.getUTCFullYear() === Number(year) && date.getUTCMonth() === Number(month) - 1 && date.getUTCDate() === Number(day);
}
