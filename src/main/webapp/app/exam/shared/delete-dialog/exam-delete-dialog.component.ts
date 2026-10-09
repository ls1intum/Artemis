import { Component, DestroyRef, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Observable, Subscription } from 'rxjs';
import { faCheck, faTimes, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective, TumAetUiCheckboxComponent, TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ConfirmEntityNameComponent } from 'app/shared-ui/confirm-entity-name/confirm-entity-name.component';
import { ActionType, EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/** Header translation key of the dialog for each action. */
const HEADER_KEYS: Record<ActionType, string> = {
    [ActionType.Delete]: 'entity.delete.title',
    [ActionType.Reset]: 'entity.reset.title',
    [ActionType.Cleanup]: 'entity.cleanup.title',
    [ActionType.Remove]: 'entity.remove.title',
    [ActionType.Unlink]: 'entity.unlink.title',
    [ActionType.NoButtonTextDelete]: 'entity.noButtonTextDelete.title',
    [ActionType.EndNow]: 'entity.endNow.title',
};

/** Label translation key of the confirming button for each action. */
const ACTION_KEYS: Record<ActionType, string> = {
    [ActionType.Delete]: 'entity.action.delete',
    [ActionType.Reset]: 'entity.action.reset',
    [ActionType.Cleanup]: 'entity.action.cleanup',
    [ActionType.Remove]: 'entity.action.remove',
    [ActionType.Unlink]: 'entity.action.unlink',
    [ActionType.NoButtonTextDelete]: 'entity.action.delete',
    [ActionType.EndNow]: 'entity.action.endNow',
};

/**
 * Asks the instructor to confirm a destructive action on an exam, its students, exercise groups or test runs: the question, an optional
 * summary of what is affected, optional extra checks and an optional entity name that has to be typed. It replaces the shared
 * PrimeNG delete dialog for the exam mode. The parent opens it with {@link visible}, reacts to {@link confirmed} and reports the
 * outcome of the action on {@link dialogError}: an empty message for success, an error message otherwise.
 */
@Component({
    selector: 'jhi-exam-delete-dialog',
    templateUrl: './exam-delete-dialog.component.html',
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ConfirmEntityNameComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiDialogComponent,
    ],
})
export class ExamDeleteDialogComponent {
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    readonly visible = model(false);
    readonly entityTitle = input('');
    /** Translation key of the question. */
    readonly deleteQuestion = input.required<string>();
    readonly translateValues = input<Record<string, unknown>>({});
    /** Translation key of the request to type the entity name. Without it, nothing has to be typed. */
    readonly deleteConfirmationText = input<string>();
    /** Translation key of the title above the summary. */
    readonly entitySummaryTitle = input<string>();
    readonly fetchEntitySummary = input<Observable<EntitySummary>>();
    /** Extra checks, as the name of the check mapped to the translation key of its label. */
    readonly additionalChecks = input<Record<string, string>>();
    readonly actionType = input<ActionType>(ActionType.Delete);
    /** Asks for the name only if one of the extra checks is selected. */
    readonly requireConfirmationOnlyForAdditionalChecks = input(false);
    /** Emits an empty message when the confirmed action succeeded and an error message when it failed. */
    readonly dialogError = input<Observable<string>>();
    /** Emits the state of the extra checks when the action is confirmed. */
    readonly confirmed = output<Record<string, boolean>>();

    readonly isLoadingSummary = signal(false);
    readonly entitySummary = signal<EntitySummary | undefined>(undefined);
    readonly checkValues = signal<Record<string, boolean>>({});
    confirmEntityName = '';

    readonly headerKey = computed(() => HEADER_KEYS[this.actionType()]);
    readonly actionKey = computed(() => ACTION_KEYS[this.actionType()]);
    readonly isDestructive = computed(() => this.actionType() !== ActionType.EndNow);
    readonly icon = computed(() => {
        switch (this.actionType()) {
            case ActionType.Reset:
                return faUndo;
            case ActionType.Cleanup:
            case ActionType.Unlink:
                return faTimes;
            case ActionType.EndNow:
                return faCheck;
            default:
                return faTrash;
        }
    });
    readonly additionalCheckKeys = computed(() => {
        const checks = this.additionalChecks() ?? {};
        return Object.keys(checks).filter((key) => !!checks[key]);
    });
    readonly summaryKeys = computed(() => {
        const summary = this.entitySummary() ?? {};
        return Object.keys(summary).filter((key) => summary[key] !== undefined);
    });
    readonly translationValues = computed(() => cloneWith(this.translateValues(), { title: this.entityTitle() }));
    readonly needsEntityName = computed(() => {
        const anyCheck = Object.values(this.checkValues()).some(Boolean);
        return !!this.deleteConfirmationText() && (!this.requireConfirmationOnlyForAdditionalChecks() || anyCheck);
    });

    private summarySubscription: Subscription | undefined;
    private errorSubscription: Subscription | undefined;

    constructor() {
        // Every opening starts with an empty form and a fresh summary.
        effect(() => {
            if (!this.visible()) {
                return;
            }
            untracked(() => {
                this.confirmEntityName = '';
                this.checkValues.set(Object.fromEntries(Object.keys(this.additionalChecks() ?? {}).map((key) => [key, false])));
                this.loadSummary();
            });
        });
        this.destroyRef.onDestroy(() => {
            this.summarySubscription?.unsubscribe();
            this.errorSubscription?.unsubscribe();
        });
    }

    setCheck(key: string, checked: boolean): void {
        this.checkValues.update((values) => cloneWith(values, { [key]: checked }));
    }

    clear(): void {
        this.visible.set(false);
    }

    /**
     * Closes the dialog right away, reports the confirmed action and shows the error of a failed action as an alert,
     * since the dialog is gone by then.
     */
    confirm(): void {
        this.alertService.closeAll();
        this.errorSubscription?.unsubscribe();
        const error$ = this.dialogError();
        if (error$) {
            this.errorSubscription = error$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((message) => {
                if (message !== '') {
                    this.alertService.error(message);
                }
                this.errorSubscription?.unsubscribe();
                this.errorSubscription = undefined;
            });
        }
        this.visible.set(false);
        this.confirmed.emit(this.checkValues());
    }

    private loadSummary(): void {
        this.summarySubscription?.unsubscribe();
        this.entitySummary.set(undefined);
        const summary$ = this.fetchEntitySummary();
        if (!summary$) {
            this.isLoadingSummary.set(false);
            return;
        }
        this.isLoadingSummary.set(true);
        this.summarySubscription = summary$.subscribe({
            next: (summary) => {
                this.entitySummary.set(summary);
                this.isLoadingSummary.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.isLoadingSummary.set(false);
                this.alertService.error('error.unexpectedError', { error: error.message });
            },
        });
    }
}
