import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { Directive, ElementRef, EventEmitter, HostListener, Input, OnInit, Output, Renderer2, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';

@Directive({ selector: '[jhiDeleteButton]' })
export class DeleteButtonDirective implements OnInit {
    private deleteDialogService = inject(DeleteDialogService);
    private renderer = inject(Renderer2);
    private elementRef = inject(ElementRef);
    private translateService = inject(TranslateService);

    @Input() entityTitle?: string;
    @Input() deleteQuestion: string;
    @Input() translateValues: { [key: string]: unknown } = {};
    @Input() deleteConfirmationText: string;
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;
    @Input() additionalChecks?: { [key: string]: string };
    @Input() actionType: ActionType = ActionType.Delete;
    @Input() buttonType: ButtonType = ButtonType.ERROR;
    @Input() renderButtonStyle = true;
    @Input() renderButtonText = true;
    @Input() requireConfirmationOnlyForAdditionalChecks = false;
    @Input() dialogError: Observable<string>;
    @Output() delete = new EventEmitter<{ [key: string]: boolean }>();
    @Input() animation = true;

    deleteTextSpan: HTMLElement;

    /**
     * This method appends classes and type property to the button on which directive was used, additionally adds a span tag with delete text.
     * We can't use component, as Angular would wrap it in its own tag and this will break button grouping that we are using for other buttons.
     */
    ngOnInit() {
        // set button classes and submit property
        if (this.renderButtonStyle) {
            this.renderer.addClass(this.elementRef.nativeElement, 'btn');
            this.renderer.addClass(this.elementRef.nativeElement, this.buttonType);
            this.renderer.addClass(this.elementRef.nativeElement, this.buttonSize);
            this.renderer.addClass(this.elementRef.nativeElement, 'me-1');
        }
        this.renderer.setProperty(this.elementRef.nativeElement, 'type', 'submit');

        // create a span with delete text
        if (this.renderButtonText) {
            this.deleteTextSpan = this.renderer.createElement('span');
            if (this.buttonType === ButtonType.ERROR) {
                this.renderer.addClass(this.deleteTextSpan, 'd-none');
            }
            this.renderer.addClass(this.deleteTextSpan, 'text-white');
            this.renderer.addClass(this.deleteTextSpan, 'd-xl-inline');
            this.setTextContent();
            this.renderer.appendChild(this.elementRef.nativeElement, this.deleteTextSpan);

            // update the span title on each language change
            this.translateService.onLangChange.subscribe(() => {
                this.setTextContent();
            });
        }
    }

    /**
     * Opens delete dialog
     */
    openDeleteDialog() {
        const deleteDialogData: DeleteDialogData = {
            entityTitle: this.entityTitle,
            deleteQuestion: this.deleteQuestion,
            translateValues: this.translateValues,
            deleteConfirmationText: this.deleteConfirmationText,
            additionalChecks: this.additionalChecks,
            actionType: this.actionType,
            buttonType: this.buttonType,
            delete: this.delete,
            dialogError: this.dialogError,
            requireConfirmationOnlyForAdditionalChecks: this.requireConfirmationOnlyForAdditionalChecks,
        };
        this.deleteDialogService.openDeleteDialog(deleteDialogData, this.animation);
    }

    /**
     * Function is executed when a MouseEvent is registered. Opens the delete Dialog
     * @param event
     */
    @HostListener('click', ['$event'])
    onClick(event: MouseEvent) {
        event.preventDefault();
        this.openDeleteDialog();
    }

    private setTextContent() {
        this.renderer.setProperty(this.deleteTextSpan, 'textContent', this.translateService.instant(`entity.action.${this.actionType}`));
    }
}
