import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { Input, Directive, HostListener, Renderer2, ElementRef, OnInit, EventEmitter, Output, OnChanges, SimpleChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ActionType, DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';

@Directive({ selector: '[jhiDeleteButton]' })
export class DeleteButtonDirective implements OnInit, OnChanges {
    @Input() entityTitle: string;
    @Input() deleteQuestion: string;
    @Input() deleteConfirmationText: string;
    @Input() additionalChecks?: { [key: string]: string };
    @Input() actionType: ActionType = ActionType.Delete;
    @Input() deleteAction: any;
    @Output() delete = new EventEmitter<{ [key: string]: boolean }>();
    @Input() close: boolean | string;
    @Input() entityParameter: any;

    deleteTextSpan: HTMLElement;

    constructor(private deleteDialogService: DeleteDialogService, private renderer: Renderer2, private el: ElementRef, private translateService: TranslateService) {}

    /**
     * This method appends classes and type property to the button on which directive was used, additionally adds a span tag with delete text.
     * We can't use component, as Angular would wrap it in it's own tag and this will break button grouping that we are using for other buttons.
     */
    ngOnInit() {
        // set button classes and submit property
        this.renderer.addClass(this.el.nativeElement, 'btn');
        this.renderer.addClass(this.el.nativeElement, 'btn-danger');
        this.renderer.addClass(this.el.nativeElement, 'btn-sm');
        this.renderer.addClass(this.el.nativeElement, 'mr-1');
        this.renderer.setProperty(this.el.nativeElement, 'type', 'submit');

        // create a span with delete text
        this.deleteTextSpan = this.renderer.createElement('span');
        this.renderer.addClass(this.deleteTextSpan, 'd-none');
        this.renderer.addClass(this.deleteTextSpan, 'd-md-inline');
        this.setTextContent();
        this.renderer.appendChild(this.el.nativeElement, this.deleteTextSpan);

        // update the span title on each language change
        this.translateService.onLangChange.subscribe(() => {
            this.setTextContent();
        });
    }

    /**
     * Opens delete dialog
     */
    openDeleteDialog() {
        const deleteDialogData: DeleteDialogData = {
            entityTitle: this.entityTitle,
            deleteQuestion: this.deleteQuestion,
            deleteConfirmationText: this.deleteConfirmationText,
            additionalChecks: this.additionalChecks,
            actionType: this.actionType,
            delete: this.delete,
        };
        this.deleteDialogService.openDeleteDialog(deleteDialogData).subscribe();
    }

    @HostListener('click')
    onClick() {
        this.openDeleteDialog();
    }

    private setTextContent() {
        this.renderer.setProperty(this.deleteTextSpan, 'textContent', this.translateService.instant(`entity.action.${this.actionType}`));
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.close && changes.close.currentValue) {
            if (typeof changes.close.currentValue === 'string') {
                this.deleteDialogService.showAlert(changes.close.currentValue);
            } else {
                this.deleteDialogService.closeDialog();
            }
        }
    }
}
