import { Directive, ElementRef, EventEmitter, HostListener, Input, OnInit, Output, Renderer2, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { DataExportConfirmationDialogData } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.model';
import { DataExportConfirmationDialogService } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.service';

@Directive({
    selector: '[jhiDataExportRequestButton]',
    standalone: true,
})
export class DataExportRequestButtonDirective implements OnInit {
    private dataExportConfirmationDialogService = inject(DataExportConfirmationDialogService);
    private renderer = inject(Renderer2);
    private elementRef = inject(ElementRef);
    private translateService = inject(TranslateService);

    @Input() expectedLogin: string;
    @Input() dialogError: Observable<string>;
    @Input() adminDialog = false;
    @Output() dataExportRequest = new EventEmitter<void>();
    @Output() dataExportRequestForAnotherUser = new EventEmitter<string>();

    private buttonText: HTMLElement;

    /**
     * This method appends classes and type property to the button on which directive was used, additionally adds a span tag with delete text.
     * We can't use component, as Angular would wrap it in its own tag and this will break button grouping that we are using for other buttons.
     */
    ngOnInit() {
        // set button classes and submit property
        this.renderer.addClass(this.elementRef.nativeElement, 'btn');
        this.renderer.addClass(this.elementRef.nativeElement, 'btn-primary');
        this.renderer.addClass(this.elementRef.nativeElement, 'btn-lg');
        this.renderer.addClass(this.elementRef.nativeElement, 'me-1');
        this.renderer.setProperty(this.elementRef.nativeElement, 'type', 'submit');

        // create a span with confirmation text
        this.buttonText = this.renderer.createElement('span');
        this.renderer.addClass(this.buttonText, 'd-xl-inline');
        this.setTextContent();
        this.renderer.appendChild(this.elementRef.nativeElement, this.buttonText);

        // update the span title on each language change
        this.translateService.onLangChange.subscribe(() => {
            this.setTextContent();
        });
    }

    /**
     * Opens confirmation dialog
     */
    openConfirmationDialog() {
        const dataExportConfirmationDialogData: DataExportConfirmationDialogData = {
            userLogin: this.expectedLogin,
            dataExportRequest: this.dataExportRequest,
            dataExportRequestForAnotherUser: this.dataExportRequestForAnotherUser,
            dialogError: this.dialogError,
            adminDialog: this.adminDialog,
        };
        this.dataExportConfirmationDialogService.openConfirmationDialog(dataExportConfirmationDialogData);
    }

    /**
     * Function is executed when a MouseEvent is registered. Opens the confirmation Dialog
     * @param event
     */
    @HostListener('click', ['$event'])
    onClick(event: MouseEvent) {
        event.preventDefault();
        this.openConfirmationDialog();
    }

    private setTextContent() {
        this.renderer.setProperty(this.buttonText, 'textContent', this.translateService.instant(`artemisApp.dataExport.request`));
    }
}
