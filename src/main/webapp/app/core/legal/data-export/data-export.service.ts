import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { Observable } from 'rxjs';
import { DataExport } from 'app/entities/data-export.model';

@Injectable({ providedIn: 'root' })
export class DataExportService {
    constructor(private http: HttpClient, private accountService: AccountService, private alertService: AlertService) {}

    requestDataExport(): Observable<DataExport> {
        const userId = this.accountService.userIdentity?.id;
        return this.http.put<DataExport>(`api/${userId}/data-export`, {});
    }

    downloadDataExport(dataExportId: number): Observable<HttpResponse<Blob>> {
        const userId = this.accountService.userIdentity?.id;
        return this.http.get(`api/${userId}/data-export/${dataExportId}`, {
            observe: 'response',
            responseType: 'blob',
        });
    }
}
