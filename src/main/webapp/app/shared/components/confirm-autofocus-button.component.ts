import { Component, EventEmitter, Input, Output, TemplateRef, ViewChild, inject } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';

@Component({
    selector: 'jhi-confirm-button',
    templateUrl: './confirm-autofocus-button.component.html',
})
export class ConfirmAutofocusButtonComponent {
    private modalService = inject(NgbModal);

    @Input() icon: IconProp;
    @Input() title: string;
    @Input() tooltip: string;
    @Input() disabled = false;
    @Input() isLoading = false;

    @Input() confirmationTitle: string;
    @Input() confirmationTitleTranslationParams?: Record<string, string>;
    @Input() confirmationText: string;
    @Input() translateText?: boolean;
    @Input() textIsMarkdown?: boolean;
    @Output() onConfirm = new EventEmitter<void>();
    @Output() onCancel = new EventEmitter<void>();

    @ViewChild('content') content?: TemplateRef<any>;

    /**
     * open confirmation modal with text and title
     */
    onOpenConfirmationModal() {
        const modalRef: NgbModalRef = this.modalService.open(ConfirmAutofocusModalComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        if (this.textIsMarkdown === true) {
            modalRef.componentInstance.text = htmlForMarkdown(this.confirmationText);
            modalRef.componentInstance.textIsMarkdown = true;
        } else {
            modalRef.componentInstance.text = this.confirmationText;
            modalRef.componentInstance.textIsMarkdown = false;
        }
        modalRef.componentInstance.title = this.confirmationTitle;
        modalRef.componentInstance.titleTranslationParams = this.confirmationTitleTranslationParams;
        if (this.translateText !== undefined) {
            modalRef.componentInstance.translateText = this.translateText;
        } else {
            modalRef.componentInstance.translateText = false;
        }
        modalRef.componentInstance.contentRef = this.content;
        modalRef.result.then(
            () => {
                this.onConfirm.emit();
            },
            () => {
                this.onCancel.emit();
            },
        );
    }
}
