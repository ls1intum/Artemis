import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AdminDataExport, DataExport } from 'app/core/shared/entities/data-export.model';
import { PageableResult } from 'app/shared/table/pageable-table';
import dayjs from 'dayjs/esm';

/**
 * Service for admin-level data export operations.
 *
 * This service provides functionality for administrators to:
 * - View all data exports across all users (with pagination)
 * - Create data exports for any user (scheduled or immediate)
 * - Download data exports for any user
 *
 * All operations require admin privileges and are protected by @EnforceAdmin on the server.
 */
@Injectable({ providedIn: 'root' })
export class AdminDataExportsService {
    private http = inject(HttpClient);

    private readonly resourceUrl = 'api/core/admin/data-exports';

    /**
     * Retrieves data exports in the system with associated user information, with pagination.
     * The exports are ordered by creation date (newest first).
     *
     * The server returns the list in the body and pagination info in headers:
     * - X-Total-Count: total number of records
     *
     * @param page The page number (0-indexed)
     * @param size The number of items per page
     * @returns Observable of PageableResult containing AdminDataExport items
     */
    getAllDataExports(page: number = 0, size: number = 20): Observable<PageableResult<AdminDataExport>> {
        const params = new HttpParams().set('page', page.toString()).set('size', size.toString());

        return this.http.get<AdminDataExport[]>(this.resourceUrl, { params, observe: 'response' }).pipe(
            map((response: HttpResponse<AdminDataExport[]>) => {
                const totalCount = parseInt(response.headers.get('X-Total-Count') ?? '0', 10);
                const content = this.convertDates(response.body ?? []);
                const totalPages = Math.ceil(totalCount / size);

                return {
                    content,
                    totalElements: totalCount,
                    totalPages,
                };
            }),
        );
    }

    /**
     * Requests a data export for a specific user as an administrator.
     *
     * @param login The login of the user to create the export for
     * @param executeNow If true, the export is created immediately; if false, it will be
     *                   processed during the next scheduled run (typically at 4 AM)
     * @returns Observable of the created DataExport
     */
    requestDataExportForUser(login: string, executeNow: boolean): Observable<DataExport> {
        const url = `${this.resourceUrl}/${login}`;
        return this.http.post<DataExport>(url, {}, { params: { executeNow: executeNow.toString() } });
    }

    /**
     * Initiates a download of a data export by opening it in a new browser window.
     * The server will return the ZIP file as an attachment.
     *
     * @param dataExportId The ID of the data export to download
     */
    downloadDataExport(dataExportId: number): void {
        const url = `${this.resourceUrl}/${dataExportId}/download`;
        window.open(url, '_blank');
    }

    /**
     * Cancels a pending data export by deleting it from the database.
     * Only data exports in REQUESTED or IN_CREATION state can be cancelled.
     *
     * @param dataExportId The ID of the data export to cancel
     * @returns Observable that completes when the export is cancelled
     */
    cancelDataExport(dataExportId: number): Observable<void> {
        const url = `${this.resourceUrl}/${dataExportId}`;
        return this.http.delete<void>(url);
    }

    /**
     * Converts date strings from the server response to dayjs objects
     * for proper date handling in the Angular application.
     *
     * @param exports Array of data exports with date strings
     * @returns Array of data exports with dayjs date objects
     */
    private convertDates(exports: AdminDataExport[]): AdminDataExport[] {
        return exports.map((dataExport) => ({
            ...dataExport,
            createdDate: dataExport.createdDate ? dayjs(dataExport.createdDate) : undefined,
            creationFinishedDate: dataExport.creationFinishedDate ? dayjs(dataExport.creationFinishedDate) : undefined,
        }));
    }
}
