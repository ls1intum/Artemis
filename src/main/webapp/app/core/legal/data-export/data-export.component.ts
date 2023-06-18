import { Component, OnInit } from '@angular/core';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';

@Component({
    selector: 'jhi-data-export',
    templateUrl: './data-export.component.html',
})
export class DataExportComponent implements OnInit {
    readonly ActionType = ActionType;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;

    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    canDownload = false;

    currentLogin: string | undefined;
    isAdmin = false;
    dataExportId: number;

    constructor(private dataExportService: DataExportService, private accountService: AccountService, private alertService: AlertService) {}

    ngOnInit() {
        this.currentLogin = this.accountService.userIdentity?.login;
        this.isAdmin = this.accountService.isAdmin();
    }

    requestExport() {
        this.dataExportService.requestDataExport().subscribe({
            next: (response) => {
                this.dialogErrorSource.next('');
                this.alertService.success('artemisApp.dataExport.requestSuccess');
                this.canDownload = true;
                this.dataExportId = response.id!;
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                this.alertService.error('artemisApp.dataExport.requestError');
            },
        });
    }

    downloadDataExport() {
        this.dataExportService.downloadDataExport(this.dataExportId).subscribe((response) => {
            downloadZipFileFromResponse(response);
            this.alertService.success('artemisApp.dataExport.downloadSuccess');
        });
    }
}
