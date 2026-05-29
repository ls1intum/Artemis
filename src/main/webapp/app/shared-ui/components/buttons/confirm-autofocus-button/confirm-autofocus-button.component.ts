import { Component, TemplateRef, inject, input, output, viewChild } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { ConfirmAutofocusModalComponent } from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { ButtonComponent, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-confirm-button',
    templateUrl: './confirm-autofocus-button.component.html',
    imports: [ButtonComponent],
})
export class ConfirmAutofocusButtonComponent {
    // NOTE: This button intentionally still uses NgbModal to open ConfirmAutofocusModalComponent.
    // ConfirmAutofocusModalComponent is a shared dialog opened by several callers, two of which live
    // in the deferred programming/** carve-out (and others in separate migration groups: atlas, quiz,
    // plagiarism). All of them rely on the `modalRef.componentInstance.* = ...` write contract, which
    // would silently break if the dialog's @Inputs became signal inputs or it switched to PrimeNG's
    // DynamicDialog. Migrating it here would require editing carve-out files, so it is DEFERRED until
    // the carve-out lifts and all callers can be migrated together.
    private modalService = inject(NgbModal);

    icon = input<IconProp | undefined>(undefined);
    title = input<string>('');
    tooltip = input<string>('');
    disabled = input(false);
    isLoading = input(false);
    btnType = input(ButtonType.PRIMARY);

    confirmationTitle = input<string>('');
    confirmationTitleTranslationParams = input<Record<string, string>>();
    confirmationText = input<string>('');
    translateText = input<boolean>(false);
    textIsMarkdown = input<boolean>(false);
    onConfirm = output<void>();
    onCancel = output<void>();

    content = viewChild<TemplateRef<any>>('content');

    /**
     * open confirmation modal with text and title
     */
    onOpenConfirmationModal() {
        const modalRef: NgbModalRef = this.modalService.open(ConfirmAutofocusModalComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        const isMarkdown = this.textIsMarkdown();
        modalRef.componentInstance.text = isMarkdown ? htmlForMarkdown(this.confirmationText()) : this.confirmationText();
        modalRef.componentInstance.textIsMarkdown = isMarkdown;
        modalRef.componentInstance.title = this.confirmationTitle();
        modalRef.componentInstance.titleTranslationParams = this.confirmationTitleTranslationParams();
        modalRef.componentInstance.translateText = this.translateText();
        modalRef.componentInstance.contentRef = this.content();
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
