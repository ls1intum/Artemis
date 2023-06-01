import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DataExport } from 'app/entities/data-export.model';

@Injectable({ providedIn: 'root' })
export class DataExportService {
    constructor(private http: HttpClient) {}

    requestDataExport(): Observable<DataExport> {
        return this.http.put<DataExport>(`api/data-exports`, {});
    }

    downloadDataExport(dataExportId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`api/data-exports/${dataExportId}`, {
            observe: 'response',
            responseType: 'blob',
        });
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
}
