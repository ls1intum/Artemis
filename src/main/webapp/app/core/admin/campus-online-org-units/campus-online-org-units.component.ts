import { Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { faFileImport, faPlus, faSpinner, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { parse } from 'papaparse';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { TableModule } from 'primeng/table';
import { CampusOnlineOrgUnit, CampusOnlineOrgUnitImportDTO, CampusOnlineService } from 'app/core/course/manage/services/campus-online.service';

// 'external_id' is not needed because transformHeader strips underscores, so 'external_id' becomes 'externalid'.
// 'id' is placed last to avoid false matches with generic ID columns.
const POSSIBLE_EXTERNAL_ID_HEADERS = ['externalid', 'porgnr', 'orgnr', 'number', 'nummer', 'id'];
const POSSIBLE_NAME_HEADERS = ['name', 'title', 'bezeichnung', 'label', 'orgunit', 'organizationalunit'];

@Component({
    selector: 'jhi-campus-online-org-units',
    templateUrl: './campus-online-org-units.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, DeleteButtonDirective, AdminTitleBarTitleDirective, AdminTitleBarActionsDirective, TableModule],
})
export class CampusOnlineOrgUnitsComponent implements OnInit {
    private readonly campusOnlineService = inject(CampusOnlineService);
    private readonly alertService = inject(AlertService);

    readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

    readonly orgUnits = signal<CampusOnlineOrgUnit[]>([]);
    readonly isImporting = signal(false);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    protected readonly faPlus = faPlus;
    protected readonly faTimes = faTimes;
    protected readonly faWrench = faWrench;
    protected readonly faFileImport = faFileImport;
    protected readonly faSpinner = faSpinner;

    ngOnInit(): void {
        this.campusOnlineService.getOrgUnits().subscribe({
            next: (orgUnits) => {
                this.orgUnits.set(orgUnits);
            },
            error: (error: HttpErrorResponse) => {
                this.alertService.error(error.error?.message ?? error.message);
            },
        });
    }

    deleteOrgUnit(orgUnitId: number): void {
        this.campusOnlineService.deleteOrgUnit(orgUnitId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.orgUnits.set(this.orgUnits().filter((ou) => ou.id !== orgUnitId));
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
            },
        });
    }

    onCSVFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) {
            return;
        }

        this.isImporting.set(true);

        const reader = new FileReader();
        reader.onload = () => {
            this.parseAndImport(reader.result as string);
            // Reset file input so the same file can be re-selected
            input.value = '';
        };
        reader.onerror = () => {
            this.isImporting.set(false);
            this.alertService.error('artemisApp.campusOnlineOrgUnits.importParseFailed', { message: 'Failed to read file' });
        };
        reader.readAsText(file);
    }

    private parseAndImport(csvContent: string): void {
        parse(csvContent, {
            header: true,
            transformHeader: (header: string) =>
                header
                    .toLowerCase()
                    .trim()
                    .replace(/[\s_-]/g, ''),
            skipEmptyLines: true,
            complete: (results) => {
                const rows = results.data as Record<string, string>[];
                const importDTOs = this.mapCsvRows(rows);

                if (importDTOs.length === 0) {
                    this.isImporting.set(false);
                    this.alertService.error('artemisApp.campusOnlineOrgUnits.importNoValidRows');
                    return;
                }

                this.campusOnlineService.importOrgUnits(importDTOs).subscribe({
                    next: (created) => {
                        this.isImporting.set(false);
                        // Reload full list
                        this.campusOnlineService.getOrgUnits().subscribe({
                            next: (orgUnits) => this.orgUnits.set(orgUnits),
                            error: (err: HttpErrorResponse) => this.alertService.error(err.error?.message ?? err.message),
                        });
                        const skipped = importDTOs.length - created.length;
                        this.alertService.success('artemisApp.campusOnlineOrgUnits.importSuccess', { created: created.length, skipped });
                    },
                    error: (error: HttpErrorResponse) => {
                        this.isImporting.set(false);
                        this.alertService.error(error.error?.message ?? error.message);
                    },
                });
            },
            error: (error: { message: string }) => {
                this.isImporting.set(false);
                this.alertService.error('artemisApp.campusOnlineOrgUnits.importParseFailed', { message: error.message });
            },
        });
    }

    private mapCsvRows(rows: Record<string, string>[]): CampusOnlineOrgUnitImportDTO[] {
        const result: CampusOnlineOrgUnitImportDTO[] = [];
        for (const row of rows) {
            const externalId = this.findValue(row, POSSIBLE_EXTERNAL_ID_HEADERS);
            const name = this.findValue(row, POSSIBLE_NAME_HEADERS);
            if (externalId && name) {
                result.push({ externalId, name });
            }
        }
        return result;
    }

    private findValue(row: Record<string, string>, possibleHeaders: string[]): string | undefined {
        for (const header of possibleHeaders) {
            const value = row[header]?.trim();
            if (value) {
                return value;
            }
        }
        return undefined;
    }
}
