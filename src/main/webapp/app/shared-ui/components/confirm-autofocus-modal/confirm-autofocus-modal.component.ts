import { Component, ElementRef, TemplateRef, afterNextRender, inject, viewChild } from '@angular/core';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { NgTemplateOutlet } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export interface ConfirmAutofocusModalData {
    title: string;
    titleTranslationParams?: Record<string, string>;
    text: string;
    translateText?: boolean;
    textIsMarkdown?: boolean;
    contentRef?: TemplateRef<any>;
    confirmDisabled?: boolean;
}

export interface ConfirmAutofocusModalResult {
    confirmed: boolean;
}

export function openConfirmAutofocusDialog(dialogService: DialogService, data: ConfirmAutofocusModalData, options: Partial<DynamicDialogConfig> = {}): DynamicDialogRef | null {
    return dialogService.open(ConfirmAutofocusModalComponent, {
        width: '50rem',
        modal: true,
        closable: false,
        closeOnEscape: true,
        dismissableMask: false,
        ...options,
        data: {
            translateText: false,
            textIsMarkdown: false,
            confirmDisabled: false,
            ...data,
        } satisfies ConfirmAutofocusModalData,
    });
}

@Component({
    selector: 'jhi-confirm-modal',
    templateUrl: './confirm-autofocus-modal.component.html',
    imports: [NgTemplateOutlet, TranslateDirective, ArtemisTranslatePipe],
})
export class ConfirmAutofocusModalComponent {
    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private readonly confirmButton = viewChild<ElementRef<HTMLButtonElement>>('confirmButton');

    readonly data = this.dialogConfig.data as ConfirmAutofocusModalData;

    constructor() {
        afterNextRender(() => this.confirmButton()?.nativeElement.focus());
    }

    cancel(): void {
        this.dialogRef.close({ confirmed: false } satisfies ConfirmAutofocusModalResult);
    }

    confirm(): void {
        this.dialogRef.close({ confirmed: true } satisfies ConfirmAutofocusModalResult);
    }
}
