import { Component, TemplateRef, ViewChild, inject, input, output } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-confirm-button',
    templateUrl: './confirm-autofocus-button.component.html',
    imports: [ButtonComponent],
})
export class ConfirmAutofocusButtonComponent {
    private modalService = inject(NgbModal);

    readonly icon = input<IconProp>(undefined!);
    readonly title = input<string>(undefined!);
    readonly tooltip = input<string>(undefined!);
    readonly disabled = input(false);
    readonly isLoading = input(false);
    readonly btnType = input(ButtonType.PRIMARY);

    readonly confirmationTitle = input<string>(undefined!);
    readonly confirmationTitleTranslationParams = input<Record<string, string>>();
    readonly confirmationText = input<string>(undefined!);
    readonly translateText = input<boolean>();
    readonly textIsMarkdown = input<boolean>();
    readonly onConfirm = output<void>();
    readonly onCancel = output<void>();

    @ViewChild('content') content?: TemplateRef<any>;

    /**
     * open confirmation modal with text and title
     */
    onOpenConfirmationModal() {
        const modalRef: NgbModalRef = this.modalService.open(ConfirmAutofocusModalComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        if (this.textIsMarkdown() === true) {
            modalRef.componentInstance.text = htmlForMarkdown(this.confirmationText());
            modalRef.componentInstance.textIsMarkdown = true;
        } else {
            modalRef.componentInstance.text = this.confirmationText();
            modalRef.componentInstance.textIsMarkdown = false;
        }
        modalRef.componentInstance.title = this.confirmationTitle();
        modalRef.componentInstance.titleTranslationParams = this.confirmationTitleTranslationParams();
        const translateText = this.translateText();
        if (translateText !== undefined) {
            modalRef.componentInstance.translateText = translateText;
        } else {
            modalRef.componentInstance.translateText = false;
        }
        modalRef.componentInstance.contentRef = this.content;
        modalRef.result.then(
            () => {
                // TODO: The 'emit' function requires a mandatory void argument
                this.onConfirm.emit();
            },
            () => {
                // TODO: The 'emit' function requires a mandatory void argument
                this.onCancel.emit();
            },
        );
    }
}
