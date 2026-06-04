import { Component, TemplateRef, inject, input, output, viewChild } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { DialogService } from 'primeng/dynamicdialog';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import {
    ConfirmAutofocusModalData,
    ConfirmAutofocusModalResult,
    openConfirmAutofocusDialog,
} from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { ButtonComponent, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-confirm-button',
    templateUrl: './confirm-autofocus-button.component.html',
    imports: [ButtonComponent],
})
export class ConfirmAutofocusButtonComponent {
    private dialogService = inject(DialogService);

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
        const isMarkdown = this.textIsMarkdown();
        const data: ConfirmAutofocusModalData = {
            text: isMarkdown ? htmlForMarkdown(this.confirmationText()) : this.confirmationText(),
            textIsMarkdown: isMarkdown,
            title: this.confirmationTitle(),
            titleTranslationParams: this.confirmationTitleTranslationParams(),
            translateText: this.translateText(),
            contentRef: this.content(),
            confirmDisabled: false,
        };

        const dialogRef = openConfirmAutofocusDialog(this.dialogService, data);

        dialogRef?.onClose.subscribe((result: ConfirmAutofocusModalResult | undefined) => {
            if (result?.confirmed) {
                this.onConfirm.emit();
            } else {
                this.onCancel.emit();
            }
        });
    }
}
