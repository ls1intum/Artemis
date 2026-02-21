import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY, Observable } from 'rxjs';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';

@Component({
    selector: '[jhiDataExportRequestButton]',
    template: `
        <span class="d-xl-inline">{{ buttonText() }}</span>
        <jhi-data-export-confirmation-dialog
            [(visible)]="dialogVisible"
            [expectedLogin]="expectedLogin() ?? ''"
            [adminDialog]="adminDialog()"
            [dialogError]="dialogError() ?? EMPTY"
            (dataExportRequest)="dataExportRequest.emit()"
            (dataExportRequestForAnotherUser)="dataExportRequestForAnotherUser.emit($event)"
        />
    `,
    imports: [DataExportConfirmationDialogComponent],
    host: {
        '(click)': 'onClick($event)',
        class: 'btn btn-primary btn-lg me-1',
        '[attr.type]': '"submit"',
    },
})
export class DataExportRequestButtonDirective implements OnInit {
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly expectedLogin = input<string>();
    readonly dialogError = input<Observable<string>>();
    readonly adminDialog = input(false);

    readonly dataExportRequest = output<void>();
    readonly dataExportRequestForAnotherUser = output<string>();

    readonly dialogVisible = signal(false);
    readonly buttonText = signal('');

    protected readonly EMPTY = EMPTY;

    /**
     * Initialize button text and subscribe to language changes
     */
    ngOnInit(): void {
        this.setTextContent();

        // update the button text on each language change
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.setTextContent();
        });
    }

    /**
     * Opens confirmation dialog
     */
    openConfirmationDialog(): void {
        this.dialogVisible.set(true);
    }

    /**
     * Function is executed when a MouseEvent is registered. Opens the confirmation Dialog
     * @param event
     */
    onClick(event: MouseEvent): void {
        event.preventDefault();
        this.openConfirmationDialog();
    }

    private setTextContent(): void {
        this.buttonText.set(this.translateService.instant('artemisApp.dataExport.request'));
    }
}
