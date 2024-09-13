import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';

export interface CleanupServiceExecutionRecordDTO {
    executionDate: dayjs.Dayjs;
    jobType: string;
}

@Injectable({ providedIn: 'root' })
export class DataCleanupService {
    private readonly adminResourceUrl = 'api/admin';

    constructor(private http: HttpClient) {}

    /**
     * Send POST request to delete orphaned data within a specific date range.
     */
    /**
     * Send POST request to delete orphaned data.
     * @returns An observable of type HttpResponse<CleanupServiceExecutionRecordDTO>.
     */
    deleteOrphans(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.post<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/delete-orphans`, null, {
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete plagiarism comparisons within a specific date range.
     * @param deleteFrom the start date from which plagiarism comparisons should be deleted
     * @param deleteTo the end date until which plagiarism comparisons should be deleted
     */
    deletePlagiarismComparisons(deleteFrom: string, deleteTo: string): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.post<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/delete-plagiarism-comparisons`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete non-rated results within a specific date range.
     * @param deleteFrom the start date from which non-rated results should be deleted
     * @param deleteTo the end date until which non-rated results should be deleted
     */
    deleteNonRatedResults(deleteFrom: string, deleteTo: string): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.post<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/delete-non-rated-results`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete old rated results within a specific date range.
     * @param deleteFrom the start date from which old rated results should be deleted
     * @param deleteTo the end date until which old rated results should be deleted
     */
    deleteOldRatedResults(deleteFrom: string, deleteTo: string): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.post<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/delete-old-rated-results`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete old submission versions within a specific date range.
     * @param deleteFrom the start date from which old submission versions should be deleted
     * @param deleteTo the end date until which old submission versions should be deleted
     */
    deleteOldSubmissionVersions(deleteFrom: string, deleteTo: string): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.post<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/delete-old-submission-versions`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete old feedback within a specific date range.
     * @param deleteFrom the start date from which old feedback should be deleted
     * @param deleteTo the end date until which old feedback should be deleted
     */
    deleteOldFeedback(deleteFrom: string, deleteTo: string): Observable<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return this.http.post<CleanupServiceExecutionRecordDTO>(`${this.adminResourceUrl}/delete-old-feedback`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send GET request to get the last executions.
     * @returns An observable of type HttpResponse<CleanupServiceExecutionRecordDTO[]>.
     */
    getLastExecutions(): Observable<HttpResponse<CleanupServiceExecutionRecordDTO[]>> {
        return this.http.get<CleanupServiceExecutionRecordDTO[]>(`${this.adminResourceUrl}/get-last-executions`, {
            observe: 'response',
        });
    }
}
