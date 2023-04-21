import { Component } from '@angular/core';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Subject } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { DataExportService } from 'app/shared/user-settings/data-export/data-export.service';

@Component({
    selector: 'jhi-data-export',
    templateUrl: './data-export.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class DataExportComponent {
    exportRequested = false;
    canRequestExport = true;
    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    readonly ActionType = ActionType;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;

    constructor(private dataExportService: DataExportService) {}

    requestExport() {
        this.dataExportService.requestExport();
    }
}
