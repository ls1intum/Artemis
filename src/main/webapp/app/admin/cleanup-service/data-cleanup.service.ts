import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { convertDateFromClient } from 'app/foundation/util/date.utils';

export interface CleanupServiceExecutionRecordDTO {
    executionDate: dayjs.Dayjs;
    jobType: string;
}

export interface CleanupCount {
    totalCount: number;
}

export interface OrphanCleanupCountDTO extends CleanupCount {
    orphanFeedback: number;
    orphanLongFeedbackText: number;
    orphanTextBlock: number;
    orphanStudentScore: number;
    orphanTeamScore: number;
    orphanFeedbackForOrphanResults: number;
    orphanLongFeedbackTextForOrphanResults: number;
    orphanTextBlockForOrphanResults: number;
    orphanRating: number;
    orphanResultsWithoutParticipation: number;
    orphanFeedbackMessage: number;
}

export interface PlagiarismComparisonCleanupCountDTO extends CleanupCount {
    plagiarismComparison: number;
    plagiarismElements: number;
    plagiarismSubmissions: number;
    plagiarismMatches: number;
}

export interface NonLatestNonRatedResultsCleanupCountDTO extends CleanupCount {
    longFeedbackText: number;
    textBlock: number;
    feedback: number;
}

export interface NonLatestRatedResultsCleanupCountDTO extends CleanupCount {
    longFeedbackText: number;
    textBlock: number;
    feedback: number;
}

export interface SubmissionVersionsCleanupCountDTO extends CleanupCount {
    submissionVersions: number;
}

export interface OldCoursesCleanupCountDTO extends CleanupCount {
    courses: number;
}

export interface OldFeedbackCleanupCountDTO extends CleanupCount {
    longFeedbackText: number;
    textBlock: number;
    feedback: number;
}

export interface NotEnrolledUsersCleanupCountDTO extends CleanupCount {
    users: number;
    blockedUsers: number;
}

export interface PlagiarismCasesCleanupCountDTO extends CleanupCount {
    plagiarismCases: number;
}

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
