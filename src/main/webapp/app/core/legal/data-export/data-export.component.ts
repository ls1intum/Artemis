import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { DataExportService } from 'app/core/legal/data-export/data-export.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { DataExport, DataExportState } from 'app/core/shared/entities/data-export.model';
import { ActivatedRoute } from '@angular/router';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { DataExportRequestButtonDirective } from './confirmation/data-export-request-button.directive';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-data-export',
    templateUrl: './data-export.component.html',
    imports: [DataExportRequestButtonDirective, ButtonComponent, TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class DataExportComponent implements OnInit {
    private readonly dataExportService = inject(DataExportService);
    private readonly accountService = inject(AccountService);
    private readonly alertService = inject(AlertService);
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly ActionType = ActionType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly DataExportState = DataExportState;

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    readonly canDownload = signal(false);
    readonly canRequestDataExport = signal(false);
    readonly currentLogin = signal<string | undefined>(undefined);
    readonly dataExportId = signal<number | undefined>(undefined);
    readonly downloadMode = signal(false);
    readonly titleKey = signal('');
    readonly description = signal('');
    readonly state = signal<DataExportState | undefined>(undefined);
    readonly dataExport = signal<DataExport>(new DataExport());
    readonly isAdmin = signal(false);

    ngOnInit(): void {
        this.currentLogin.set(this.accountService.userIdentity()?.login);
        this.isAdmin.set(this.accountService.isAdmin());

        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            if (params['id']) {
                this.downloadMode.set(true);
                this.dataExportId.set(params['id']);
            }
        });

        if (this.downloadMode()) {
            this.titleKey.set('artemisApp.dataExport.titleDownload');
            this.description.set('artemisApp.dataExport.descriptionDownload');
            this.dataExportService.canDownloadSpecificDataExport(this.dataExportId()!).subscribe((canDownloadDataExport) => {
                this.canDownload.set(canDownloadDataExport);
            });
        } else {
            this.titleKey.set('artemisApp.dataExport.title');
            this.description.set('artemisApp.dataExport.description');
            this.dataExportService.canRequestDataExport().subscribe((canRequestDataExport) => {
                this.canRequestDataExport.set(canRequestDataExport);
            });
            this.dataExportService.canDownloadAnyDataExport().subscribe((dataExport) => {
                this.dataExport.update((current) => ({
                    ...current,
                    createdDate: convertDateFromServer(dataExport.createdDate),
                    nextRequestDate: convertDateFromServer(dataExport.nextRequestDate),
                    dataExportState: dataExport.dataExportState,
                }));
                this.canDownload.set(!!dataExport.id);
                if (dataExport.id) {
                    this.dataExport.update((current) => ({ ...current, id: dataExport.id }));
                    this.dataExportId.set(dataExport.id);
                }
                this.state.set(dataExport.dataExportState);
            });
        }
    }

    requestExport(): void {
        this.dataExportService.requestDataExport().subscribe({
            next: (response: DataExport) => {
                this.dialogErrorSource.next('');
                this.alertService.success('artemisApp.dataExport.requestSuccess');
                this.dataExportId.set(response.id);
                this.canRequestDataExport.set(false);
                this.state.set(response.dataExportState);
                this.dataExport.update((current) => ({ ...current, createdDate: response.createdDate }));
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                this.alertService.error('artemisApp.dataExport.requestError');
            },
        });
    }

    downloadDataExport(): void {
        const id = this.dataExportId();
        if (id) {
            this.dataExportService.downloadDataExport(id);
        }
    }

    requestExportForAnotherUser(login: string): void {
        this.dataExportService.requestDataExportForAnotherUser(login).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.alertService.success('artemisApp.dataExport.requestForUserSuccess', { login });
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                this.alertService.error('artemisApp.dataExport.requestForUserError', { login });
            },
        });
    }
}
