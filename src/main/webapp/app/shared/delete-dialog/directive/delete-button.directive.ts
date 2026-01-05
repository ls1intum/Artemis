import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { DestroyRef, Directive, ElementRef, EventEmitter, OnInit, Output, Renderer2, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { ActionType, DeleteDialogData, EntitySummary, EntitySummaryCategory } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';

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
    // Note: Using @Output here because the EventEmitter is passed through the service to DeleteDialogComponent
    // which calls .emit() on it. Signal outputs (OutputEmitterRef) don't support this pattern.
    @Output() delete = new EventEmitter<{ [key: string]: boolean }>();
    animation = input<boolean>(true);

    deleteTextSpan: HTMLElement;

    /**
     * This method appends classes and type property to the button on which directive was used, additionally adds a span tag with delete text.
     * We can't use component, as Angular would wrap it in its own tag and this will break button grouping that we are using for other buttons.
     */
    ngOnInit() {
        // set button classes and submit property
        if (this.renderButtonStyle()) {
            this.renderer.addClass(this.elementRef.nativeElement, 'btn');
            this.renderer.addClass(this.elementRef.nativeElement, this.buttonType());
            this.renderer.addClass(this.elementRef.nativeElement, this.buttonSize());
            this.renderer.addClass(this.elementRef.nativeElement, 'me-1');
        }
        this.renderer.setProperty(this.elementRef.nativeElement, 'type', 'submit');

        // create a span with delete text
        if (this.renderButtonText()) {
            this.deleteTextSpan = this.renderer.createElement('span');
            if (this.buttonType() === ButtonType.ERROR) {
                this.renderer.addClass(this.deleteTextSpan, 'd-none');
            }
            this.renderer.addClass(this.deleteTextSpan, 'text-white');
            this.renderer.addClass(this.deleteTextSpan, 'd-xl-inline');
            this.setTextContent();
            this.renderer.appendChild(this.elementRef.nativeElement, this.deleteTextSpan);

            // update the span title on each language change
            this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
                this.setTextContent();
            });
        }
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
            delete: this.delete,
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

    private setTextContent() {
        this.renderer.setProperty(this.deleteTextSpan, 'textContent', this.translateService.instant(`entity.action.${this.actionType()}`));
    }
}
