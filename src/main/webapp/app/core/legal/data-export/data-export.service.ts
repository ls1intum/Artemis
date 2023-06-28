import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DataExport } from 'app/entities/data-export.model';

@Injectable({ providedIn: 'root' })
export class DataExportService {
    constructor(private http: HttpClient) {}

    requestDataExport(): Observable<DataExport> {
        return this.http.put<DataExport>(`api/data-export`, {});
    }

    downloadDataExport(dataExportId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`api/data-export/${dataExportId}`, {
            observe: 'response',
            responseType: 'blob',
        });
    }
}
