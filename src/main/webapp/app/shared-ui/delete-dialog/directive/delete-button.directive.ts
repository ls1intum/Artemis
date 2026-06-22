import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { DestroyRef, Directive, ElementRef, OnInit, Renderer2, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { ActionType, DeleteDialogData, EntitySummary, EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { Observable } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

/**
 * Maps the legacy Bootstrap {@link ButtonType} values to PrimeNG button severity classes so that the
 * delete button matches the `pButton` directive's output (e.g. View/Edit buttons rendered with
 * `severity="info"` / `severity="warn"`), including inside a `<p-buttongroup>`.
 */
const BUTTON_TYPE_TO_PRIME_SEVERITY_CLASS: Record<ButtonType, string | undefined> = {
    [ButtonType.DEFAULT]: undefined,
    [ButtonType.PRIMARY]: undefined, // PrimeNG primary is the default (no severity class)
    [ButtonType.SECONDARY]: 'p-button-secondary',
    [ButtonType.SUCCESS]: 'p-button-success',
    [ButtonType.WARNING]: 'p-button-warn',
    [ButtonType.ERROR]: 'p-button-danger',
    [ButtonType.INFO]: 'p-button-info',
    [ButtonType.PRIMARY_OUTLINE]: 'p-button-outlined',
    [ButtonType.SUCCESS_OUTLINE]: 'p-button-success p-button-outlined',
    [ButtonType.ERROR_OUTLINE]: 'p-button-danger p-button-outlined',
};

/**
 * Maps the legacy Bootstrap {@link ButtonSize} values to PrimeNG button size classes.
 * PrimeNG has no explicit "medium" size class — medium is the default, so it maps to no class.
 */
const BUTTON_SIZE_TO_PRIME_SIZE_CLASS: Record<ButtonSize, string | undefined> = {
    [ButtonSize.SMALL]: 'p-button-sm',
    // PrimeNG has no "medium"; map it to small so delete buttons match the small admin/PrimeNG toolbar norm
    // and the btn-sm Bootstrap siblings the few MEDIUM callers sit next to (instead of rendering taller).
    [ButtonSize.MEDIUM]: 'p-button-sm',
    [ButtonSize.LARGE]: 'p-button-lg',
};

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

    deleteTextSpan?: HTMLElement;

    /**
     * Styles the host as a PrimeNG button (matching what the `pButton` directive emits) and appends a
     * label span with the localized action text. We deliberately use a directive rather than a component
     * so the host stays a plain <button>, which keeps `<p-buttongroup>` working (a wrapping component tag
     * would break the group's joined-button layout).
     */
    ngOnInit() {
        // ERROR is the common row-action case: hide the label on narrow viewports (it reappears from md up),
        // mirroring the `hidden md:inline` label on neighbouring pButton actions. The button keeps NORMAL
        // padding (not icon-only) so it stays geometrically identical to those View/Edit siblings.
        const hidesTextOnNarrowViewport = this.buttonType() === ButtonType.ERROR && this.renderButtonText();
        // Square icon-only padding applies ONLY when text is never rendered — matching the pButton directive,
        // which becomes icon-only only when it has no label/text at all.
        const isEffectivelyIconOnly = !this.renderButtonText();

        if (this.renderButtonStyle()) {
            // Bootstrap is still globally loaded; strip any btn* classes the consumer hardcoded on the host so
            // they do not double-style on top of the PrimeNG classes added below (enum values ARE the bootstrap classes).
            ['btn', ...Object.values(ButtonType), ...Object.values(ButtonSize)].forEach((cls) => this.renderer.removeClass(this.elementRef.nativeElement, cls));

            this.renderer.addClass(this.elementRef.nativeElement, 'p-button');
            this.renderer.addClass(this.elementRef.nativeElement, 'p-component');

            const sizeClass = BUTTON_SIZE_TO_PRIME_SIZE_CLASS[this.buttonSize()];
            if (sizeClass) {
                sizeClass.split(' ').forEach((cls) => this.renderer.addClass(this.elementRef.nativeElement, cls));
            }

            const severityClass = BUTTON_TYPE_TO_PRIME_SEVERITY_CLASS[this.buttonType()];
            if (severityClass) {
                severityClass.split(' ').forEach((cls) => this.renderer.addClass(this.elementRef.nativeElement, cls));
            }

            if (isEffectivelyIconOnly) {
                this.renderer.addClass(this.elementRef.nativeElement, 'p-button-icon-only');
            }
        }
        this.renderer.setProperty(this.elementRef.nativeElement, 'type', 'submit');

        // create a span with delete text
        if (this.renderButtonText()) {
            this.deleteTextSpan = this.renderer.createElement('span');
            this.renderer.addClass(this.deleteTextSpan, 'p-button-label');
            if (hidesTextOnNarrowViewport) {
                // Hidden below md, shown from md upwards — consistent with sibling pButton View/Edit labels.
                this.renderer.addClass(this.deleteTextSpan, 'hidden');
                this.renderer.addClass(this.deleteTextSpan, 'md:inline');
            }
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

    private setTextContent() {
        this.renderer.setProperty(this.deleteTextSpan, 'textContent', this.translateService.instant(`entity.action.${this.actionType()}`));
    }
}
