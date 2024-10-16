import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DataExport } from 'app/entities/data-export.model';

@Injectable({ providedIn: 'root' })
export class DataExportService {
    private http = inject(HttpClient);

    requestDataExport(): Observable<DataExport> {
        return this.http.post<DataExport>(`api/data-exports`, {});
    }

    downloadDataExport(dataExportId: number) {
        const url = `api/data-exports/${dataExportId}`;
        window.open(url, '_blank');
    }

    canRequestDataExport(): Observable<boolean> {
        return this.http.get<boolean>(`api/data-exports/can-request`);
    }

    canDownloadAnyDataExport(): Observable<DataExport> {
        return this.http.get<DataExport>(`api/data-exports/can-download`);
    }

    canDownloadSpecificDataExport(dataExportId: number): Observable<boolean> {
        return this.http.get<boolean>(`api/data-exports/${dataExportId}/can-download`);
    }

    requestDataExportForAnotherUser(login: string): Observable<DataExport> {
        return this.http.post<DataExport>(`api/admin/data-exports/${login}`, {});
    }
}
