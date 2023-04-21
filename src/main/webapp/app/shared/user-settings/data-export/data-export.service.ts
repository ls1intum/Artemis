import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';

@Injectable({ providedIn: 'root' })
export class DataExportService {
    constructor(private http: HttpClient, private accountService: AccountService, private alertService: AlertService) {}

    requestExport(): void {
        const userId = this.accountService.userIdentity?.id;
        this.http.put(`api/${userId}/data-export`, {}, { observe: 'response' }).subscribe(
            () => {
                this.alertService.success('artemisApp.dataExport.requestSuccess');
            },
            () => {
                this.alertService.error('artemisApp.dataExport.requestError');
            },
        );
    }
}
