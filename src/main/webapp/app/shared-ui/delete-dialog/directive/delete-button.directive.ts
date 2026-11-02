import { tumAetUiButtonClasses } from '@tumaet/ui-angular';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { DestroyRef, Directive, ElementRef, OnInit, Renderer2, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { ActionType, DeleteDialogData, EntitySummary, EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { Observable } from 'rxjs';
import { ButtonSize, ButtonType, getTumAetUiButtonOptions } from 'app/shared-ui/components/buttons/button/button.component';

@Directive({ selector: '[jhiDeleteButton]', host: { '(click)': 'onClick($event)' } })
export class DeleteButtonDirective implements OnInit {
    private deleteDialogService = inject(DeleteDialogService);
    private renderer = inject(Renderer2);
    private elementRef = inject(ElementRef);
    private translateService = inject(TranslateService);
    private destroyRef = inject(DestroyRef);

    entityTitle = input<string>();
    deleteQuestion = input<string>();
    entitySummaryTitle = input<string>();
    fetchEntitySummary = input<Observable<EntitySummary>>();
    fetchCategorizedEntitySummary = input<Observable<EntitySummaryCategory[]>>();
    translateValues = input<{ [key: string]: unknown }>({});
    deleteConfirmationText = input<string>();
    buttonSize = input<ButtonSize>(ButtonSize.SMALL);
    additionalChecks = input<{ [key: string]: string }>();
    actionType = input<ActionType>(ActionType.Delete);
    buttonType = input<ButtonType>(ButtonType.ERROR);
    renderButtonStyle = input<boolean>(true);
    renderButtonText = input<boolean>(true);
    requireConfirmationOnlyForAdditionalChecks = input<boolean>(false);
    dialogError = input<Observable<string>>();
    delete = output<{ [key: string]: boolean }>();
    animation = input<boolean>(true);

    // Optional for callers that provide their own visible label or an icon-only presentation.
    deleteTextSpan?: HTMLElement;
    private callerProvidedAriaLabel = false;

    /** Applies package-owned button variants and keeps the translated accessible name current. */
    ngOnInit() {
        this.callerProvidedAriaLabel = !!this.elementRef.nativeElement.getAttribute('aria-label');

        if (this.renderButtonStyle()) {
            for (const token of tumAetUiButtonClasses(getTumAetUiButtonOptions(this.buttonType(), this.buttonSize())).split(' ')) {
                this.renderer.addClass(this.elementRef.nativeElement, token);
            }
            this.renderer.addClass(this.elementRef.nativeElement, 'me-1');
        }
        this.renderer.setProperty(this.elementRef.nativeElement, 'type', 'submit');

        // Callers with their own label keep full control of its responsive presentation.
        if (this.renderButtonStyle() && this.renderButtonText()) {
            this.deleteTextSpan = this.renderer.createElement('span');
            if (this.buttonType() === ButtonType.ERROR) {
                this.renderer.addClass(this.deleteTextSpan, 'hidden');
            }
            this.renderer.addClass(this.deleteTextSpan, 'xl:inline');
            this.renderer.appendChild(this.elementRef.nativeElement, this.deleteTextSpan);
        }

        this.applyActionLabel();
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.applyActionLabel());
    }

    /**
     * Opens delete dialog
     */
    openDeleteDialog() {
        const deleteDialogData: DeleteDialogData = {
            entityTitle: this.entityTitle(),
            deleteQuestion: this.deleteQuestion(),
            translateValues: this.translateValues(),
            deleteConfirmationText: this.deleteConfirmationText(),
            additionalChecks: this.additionalChecks(),
            entitySummaryTitle: this.entitySummaryTitle(),
            fetchEntitySummary: this.fetchEntitySummary(),
            fetchCategorizedEntitySummary: this.fetchCategorizedEntitySummary(),
            actionType: this.actionType(),
            buttonType: this.buttonType(),
            delete: (additionalChecksValues) => this.delete.emit(additionalChecksValues),
            dialogError: this.dialogError(),
            requireConfirmationOnlyForAdditionalChecks: this.requireConfirmationOnlyForAdditionalChecks(),
        };
        this.deleteDialogService.openDeleteDialog(deleteDialogData, this.animation());
    }

    /**
     * Function is executed when a MouseEvent is registered. Opens the delete Dialog
     * @param event
     */
    onClick(event: MouseEvent) {
        event.preventDefault();
        this.openDeleteDialog();
    }

    /** Keeps the accessible name (and the visible label span, when present) on the translated action. */
    private applyActionLabel() {
        const action = this.translateService.instant(`entity.action.${this.actionType()}`);
        if (this.deleteTextSpan) {
            this.renderer.setProperty(this.deleteTextSpan, 'textContent', action);
        }
        if (!this.callerProvidedAriaLabel) {
            this.renderer.setAttribute(this.elementRef.nativeElement, 'aria-label', action);
        }
    }
}
