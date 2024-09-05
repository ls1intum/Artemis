import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DataCleanupService {
    private readonly adminResourceUrl = 'api/admin';

    constructor(private http: HttpClient) {}

    /**
     * Send POST request to delete orphaned data within a specific date range.
     * @param deleteFrom the start date from which orphaned data should be deleted
     * @param deleteTo the end date until which orphaned data should be deleted
     */
    deleteOrphans(deleteFrom: string, deleteTo: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/delete-orphans`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete plagiarism comparisons within a specific date range.
     * @param deleteFrom the start date from which plagiarism comparisons should be deleted
     * @param deleteTo the end date until which plagiarism comparisons should be deleted
     */
    deletePlagiarismComparisons(deleteFrom: string, deleteTo: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/delete-plagiarism-comparisons`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete non-rated results within a specific date range.
     * @param deleteFrom the start date from which non-rated results should be deleted
     * @param deleteTo the end date until which non-rated results should be deleted
     */
    deleteNonRatedResults(deleteFrom: string, deleteTo: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/delete-non-rated-results`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete old rated results within a specific date range.
     * @param deleteFrom the start date from which old rated results should be deleted
     * @param deleteTo the end date until which old rated results should be deleted
     */
    deleteOldRatedResults(deleteFrom: string, deleteTo: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/delete-old-rated-results`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete old submission versions within a specific date range.
     * @param deleteFrom the start date from which old submission versions should be deleted
     * @param deleteTo the end date until which old submission versions should be deleted
     */
    deleteOldSubmissionVersions(deleteFrom: string, deleteTo: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/delete-old-submission-versions`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to delete old feedback within a specific date range.
     * @param deleteFrom the start date from which old feedback should be deleted
     * @param deleteTo the end date until which old feedback should be deleted
     */
    deleteOldFeedback(deleteFrom: string, deleteTo: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/delete-old-feedback`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }

    /**
     * Send POST request to clean up all old data within a specific date range.
     * @param deleteFrom the start date from which all old data should be cleaned up
     * @param deleteTo the end date until which all old data should be cleaned up
     */
    deleteOldData(deleteFrom: string, deleteTo: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/delete-all-old-data`, null, {
            params: { deleteFrom, deleteTo },
            observe: 'response',
        });
    }
}
