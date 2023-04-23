import { Component, OnInit } from '@angular/core';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-data-export',
    templateUrl: './data-export.component.html',
})
export class DataExportComponent implements OnInit {
    exportRequested = false;
    canRequestExport = true;
    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    readonly ActionType = ActionType;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;
    canDownload = false;

    currentLogin: string | undefined;
    isAdmin = false;

    constructor(private dataExportService: DataExportService, private accountService: AccountService, private alertService: AlertService) {}

    ngOnInit() {
        console.log(this.accountService.userIdentity);
        this.currentLogin = this.accountService.userIdentity?.login;
        this.isAdmin = this.accountService.isAdmin();
    }

    requestExport() {
        this.dataExportService.requestExport().subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.alertService.success('artemisApp.dataExport.requestSuccess');
                this.canDownload = true;
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                this.alertService.error('artemisApp.dataExport.requestError');
            },
        });
    }
}
