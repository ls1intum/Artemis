import { Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { mapValues } from 'lodash-es';
import {
    ActionType,
    DeleteDialogDeleteHandler,
    EntitySummary,
    EntitySummaryCategory,
    EntitySummaryItem,
    triggerDeleteDialogDelete,
} from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { Observable } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { faBan, faCheck, faSpinner, faTimes, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { FormsModule, NgForm } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ConfirmEntityNameComponent } from '../../confirm-entity-name/confirm-entity-name.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-delete-dialog',
    templateUrl: './delete-dialog.component.html',
    styleUrls: ['./delete-dialog.component.scss'],
    imports: [FormsModule, TranslateDirective, ConfirmEntityNameComponent, FaIconComponent, ArtemisTranslatePipe, TableModule, ButtonModule, CheckboxModule, ProgressSpinnerModule],
})
export class DeleteDialogComponent implements OnInit {
    protected readonly faBan = faBan;
    protected readonly faSpinner = faSpinner;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;
    protected readonly faCheck = faCheck;
    protected readonly faUndo = faUndo;

    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private alertService = inject(AlertService);
    private destroyRef = inject(DestroyRef);

    readonly actionTypes = ActionType;
    private delete: DeleteDialogDeleteHandler;

    // Signals for reactive state
    submitDisabled = signal(false);
    entityTitle = signal('');
    buttonType = signal<ButtonType>(ButtonType.ERROR);
    entitySummary = signal<EntitySummary | undefined>({});
    categorizedEntitySummary = signal<EntitySummaryCategory[] | undefined>(undefined);
    isLoadingSummary = signal(false);

    // Regular properties for ngModel two-way binding
    confirmEntityName = '';
    // Backed by a signal via a getter/setter facade: the template binds [(ngModel)]="additionalChecksValues[checkKey]"
    // (a deep two-way target), so reads stay reactive while ngModel can still mutate the backing object in place.
    private readonly _additionalChecksValues = signal<{ [key: string]: boolean }>({});
    get additionalChecksValues(): { [key: string]: boolean } {
        return this._additionalChecksValues();
    }
    set additionalChecksValues(value: { [key: string]: boolean }) {
        this._additionalChecksValues.set(value);
    }

    private readonly deleteFormRef = viewChild.required<NgForm>('deleteForm');

    get deleteForm(): NgForm {
        return this.deleteFormRef();
    }

    readonly deleteQuestion = signal<string>(undefined!);
    readonly entitySummaryTitle = signal<string | undefined>(undefined);
    readonly translateValues = signal<{ [key: string]: unknown }>({});
    readonly deleteConfirmationText = signal<string>(undefined!);
    readonly requireConfirmationOnlyForAdditionalChecks = signal<boolean>(undefined!);
    readonly additionalChecks = signal<{ [key: string]: string } | undefined>(undefined);
    readonly actionType = signal<ActionType>(undefined!);
    // do not use faTimes icon if it's a confirmation but not a delete dialog
    readonly useFaCheckIcon = signal<boolean>(undefined!);

    // used by @for in the template
    objectKeys = Object.keys;

    warningTextColor: string;

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit(): void {
        // Get data from DynamicDialogConfig
        const data = this.dialogConfig.data;
        this.entityTitle.set(data.entityTitle);
        this.deleteQuestion.set(data.deleteQuestion);
        this.translateValues.set(data.translateValues);
        this.deleteConfirmationText.set(data.deleteConfirmationText);
        this.additionalChecks.set(data.additionalChecks);
        this.entitySummaryTitle.set(data.entitySummaryTitle);
        this.actionType.set(data.actionType);
        this.buttonType.set(data.buttonType);
        this.delete = data.delete;
        this.requireConfirmationOnlyForAdditionalChecks.set(data.requireConfirmationOnlyForAdditionalChecks);

        // Fetch entity summary if provided
        if (data.fetchCategorizedEntitySummary) {
            this.fetchCategorizedEntitySummary(data.fetchCategorizedEntitySummary);
        } else if (data.fetchEntitySummary) {
            this.fetchEntitySummary(data.fetchEntitySummary);
        }

        // Note: Error handling is done in DeleteDialogService since the dialog closes immediately on confirm.
        // The service subscribes to dialogError and displays errors via AlertService.

        if (this.additionalChecks()) {
            this.additionalChecksValues = mapValues(this.additionalChecks(), () => false);
        }
        this.useFaCheckIcon.set(this.buttonType() !== ButtonType.ERROR);
        if (ButtonType.ERROR !== this.buttonType()) {
            this.warningTextColor = '';
        } else {
            this.warningTextColor = 'text-state-danger';
        }
    }

    /**
     * Closes the dialog
     */
    clear(): void {
        this.dialogRef.close();
    }

    /**
     * Emits delete event, closes the dialog immediately, and passes additional checks from the dialog.
     * The dialog closes right away so that the progress bar overlay can be shown during the delete operation.
     */
    confirmDelete(): void {
        this.submitDisabled.set(true);
        triggerDeleteDialogDelete(this.delete, this.additionalChecksValues);
        this.dialogRef.close();
    }

    /**
     * Check if at least one additionalCheck is selected
     */
    get isAnyAdditionalCheckSelected(): boolean {
        return Object.values(this.additionalChecksValues).some((check) => check);
    }

    /**
     * Groups items into pairs for 4-column layout display
     */
    getItemPairs(items: EntitySummaryItem[]): EntitySummaryItem[][] {
        const visibleItems = items.filter((item) => item.value !== undefined);
        const pairs: EntitySummaryItem[][] = [];
        for (let i = 0; i < visibleItems.length; i += 2) {
            pairs.push(visibleItems.slice(i, i + 2));
        }
        return pairs;
    }

    /**
     * Fetches entity summary
     */
    private fetchEntitySummary(fetchEntitySummary: Observable<EntitySummary>): void {
        this.isLoadingSummary.set(true);
        fetchEntitySummary.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: (entitySummary: EntitySummary) => {
                this.entitySummary.set(entitySummary);
                this.isLoadingSummary.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.isLoadingSummary.set(false);
                this.alertService.error('error.unexpectedError', { error: error.message });
            },
        });
    }

    /**
     * Fetches categorized entity summary
     */
    private fetchCategorizedEntitySummary(fetchCategorizedEntitySummary: Observable<EntitySummaryCategory[]>): void {
        this.isLoadingSummary.set(true);
        fetchCategorizedEntitySummary.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: (categorizedEntitySummary: EntitySummaryCategory[]) => {
                this.categorizedEntitySummary.set(categorizedEntitySummary);
                this.isLoadingSummary.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.isLoadingSummary.set(false);
                this.alertService.error('error.unexpectedError', { error: error.message });
            },
        });
    }
}
