import { Component, OnInit } from '@angular/core';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { DataExport } from 'app/entities/data-export.model';
import { ActivatedRoute } from '@angular/router';

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
    canRequestDataExport = false;

    currentLogin: string | undefined;
    dataExportId: number;
    downloadMode = false;
    titleKey: string;
    description: string;

    constructor(private dataExportService: DataExportService, private accountService: AccountService, private alertService: AlertService, private route: ActivatedRoute) {}

    ngOnInit() {
        this.currentLogin = this.accountService.userIdentity?.login;
        this.route.params.subscribe((params) => {
            if (params['id']) {
                this.downloadMode = true;
                this.dataExportId = params['id'];
            }
        });

        if (this.downloadMode) {
            this.titleKey = 'artemisApp.dataExport.titleDownload';
            this.description = 'artemisApp.dataExport.descriptionDownload';
            this.dataExportService.canDownloadSpecificDataExport(this.dataExportId).subscribe((canDownloadDataExport) => {
                this.canDownload = canDownloadDataExport;
            });
        } else {
            this.titleKey = 'artemisApp.dataExport.title';
            this.description = 'artemisApp.dataExport.description';
            this.dataExportService.canRequestDataExport().subscribe((canRequestDataExport) => {
                this.canRequestDataExport = canRequestDataExport;
            });
            this.dataExportService.canDownloadAnyDataExport().subscribe((dataExport) => {
                this.canDownload = !!dataExport.id;
                if (this.canDownload) {
                    this.dataExportId = dataExport.id!;
                }
            });
        }
    }

    requestExport() {
        this.dataExportService.requestDataExport().subscribe({
            next: (response: DataExport) => {
                this.dialogErrorSource.next('');
                this.alertService.success('artemisApp.dataExport.requestSuccess');
                this.dataExportId = response.id!;
                this.canRequestDataExport = false;
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                this.alertService.error('artemisApp.dataExport.requestError');
            },
        });
    }

    downloadDataExport() {
        this.dataExportService.downloadDataExport(this.dataExportId).subscribe((response: HttpResponse<Blob>) => {
            downloadZipFileFromResponse(response);
            this.alertService.success('artemisApp.dataExport.downloadSuccess');
        });
    }
}
