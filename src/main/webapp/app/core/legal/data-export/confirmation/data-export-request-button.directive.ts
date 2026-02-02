import { DestroyRef, Directive, ElementRef, OnInit, Renderer2, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { DataExportConfirmationDialogData } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.model';
import { DataExportConfirmationDialogService } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.service';

@Directive({
    selector: '[jhiDataExportRequestButton]',
    host: {
        '(click)': 'onClick($event)',
    },
})
export class DataExportRequestButtonDirective implements OnInit {
    private readonly dataExportConfirmationDialogService = inject(DataExportConfirmationDialogService);
    private readonly renderer = inject(Renderer2);
    private readonly elementRef = inject(ElementRef);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly expectedLogin = input<string>();
    readonly dialogError = input<Observable<string>>();
    readonly adminDialog = input(false);

    readonly dataExportRequest = output<void>();
    readonly dataExportRequestForAnotherUser = output<string>();

    private buttonText: HTMLElement;

    /**
     * This method appends classes and type property to the button on which directive was used, additionally adds a span tag with delete text.
     * We can't use component, as Angular would wrap it in its own tag and this will break button grouping that we are using for other buttons.
     */
    ngOnInit(): void {
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
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.setTextContent();
        });
    }

    /**
     * Opens confirmation dialog
     */
    openConfirmationDialog(): void {
        const dataExportConfirmationDialogData: DataExportConfirmationDialogData = {
            userLogin: this.expectedLogin(),
            dataExportRequest: this.dataExportRequest,
            dataExportRequestForAnotherUser: this.dataExportRequestForAnotherUser,
            dialogError: this.dialogError(),
            adminDialog: this.adminDialog(),
        };
        this.dataExportConfirmationDialogService.openConfirmationDialog(dataExportConfirmationDialogData);
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
        this.renderer.setProperty(this.buttonText, 'textContent', this.translateService.instant(`artemisApp.dataExport.request`));
    }
}
