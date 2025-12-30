import { Component, DestroyRef, EventEmitter, OnInit, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { mapValues } from 'lodash-es';
import { ActionType, EntitySummary, EntitySummaryCategory, EntitySummaryItem } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { faBan, faCheck, faSpinner, faTimes, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FormsModule, NgForm } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConfirmEntityNameComponent } from '../../confirm-entity-name/confirm-entity-name.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';
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
    // Note: This EventEmitter is passed from the directive via the service, not used as a template output
    private delete: EventEmitter<{ [key: string]: boolean }>;

    // Signals for reactive state
    submitDisabled = signal(false);
    entityTitle = signal('');
    buttonType = signal<ButtonType>(ButtonType.ERROR);
    entitySummary = signal<EntitySummary | undefined>({});
    categorizedEntitySummary = signal<EntitySummaryCategory[] | undefined>(undefined);
    isLoadingSummary = signal(false);

    // Regular properties for ngModel two-way binding
    confirmEntityName = '';
    additionalChecksValues: { [key: string]: boolean } = {};

    // Note: Using @ViewChild here because the template has #deleteForm="ngForm" which creates a template
    // reference variable that shadows any component property. The viewChild signal cannot be used in this case.
    @ViewChild('deleteForm', { static: true }) deleteForm: NgForm;

    deleteQuestion: string;
    entitySummaryTitle?: string;
    translateValues: { [key: string]: unknown } = {};
    deleteConfirmationText: string;
    requireConfirmationOnlyForAdditionalChecks: boolean;
    additionalChecks?: { [key: string]: string };
    actionType: ActionType;
    // do not use faTimes icon if it's a confirmation but not a delete dialog
    useFaCheckIcon: boolean;

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
        this.deleteQuestion = data.deleteQuestion;
        this.translateValues = data.translateValues;
        this.deleteConfirmationText = data.deleteConfirmationText;
        this.additionalChecks = data.additionalChecks;
        this.entitySummaryTitle = data.entitySummaryTitle;
        this.actionType = data.actionType;
        this.buttonType.set(data.buttonType);
        this.delete = data.delete;
        this.requireConfirmationOnlyForAdditionalChecks = data.requireConfirmationOnlyForAdditionalChecks;

        // Fetch entity summary if provided
        if (data.fetchCategorizedEntitySummary) {
            this.fetchCategorizedEntitySummary(data.fetchCategorizedEntitySummary);
        } else if (data.fetchEntitySummary) {
            this.fetchEntitySummary(data.fetchEntitySummary);
        }

        // Note: Error handling is done in DeleteDialogService since the dialog closes immediately on confirm.
        // The service subscribes to dialogError and displays errors via AlertService.

        if (this.additionalChecks) {
            this.additionalChecksValues = mapValues(this.additionalChecks, () => false);
        }
        this.useFaCheckIcon = this.buttonType() !== ButtonType.ERROR;
        if (ButtonType.ERROR !== this.buttonType()) {
            this.warningTextColor = 'text-default';
        } else {
            this.warningTextColor = 'text-danger';
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
        this.delete.emit(this.additionalChecksValues);
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
