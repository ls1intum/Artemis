import { Component, ElementRef, TemplateRef, afterNextRender, inject, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TumAetUiButtonDirective, TumAetUiDialogConfig, TumAetUiDialogOptions, TumAetUiDialogRef, TumAetUiDialogService } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DialogTranslateHeaderComponent } from 'app/shared-ui/dynamic-dialog/dialog-translate-header.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export interface ConfirmAutofocusModalData {
    title: string;
    titleTranslationParams?: Record<string, string>;
    text: string;
    translateText?: boolean;
    textIsMarkdown?: boolean;
    contentRef?: TemplateRef<unknown>;
    confirmDisabled?: boolean;
}

export interface ConfirmAutofocusModalResult {
    confirmed: boolean;
}

/** The data of the dialog: the caller's data plus the header keys that `DialogTranslateHeaderComponent` renders. */
type ConfirmAutofocusDialogData = ConfirmAutofocusModalData & { headerKey: string; headerParams?: Record<string, string> };

export function openConfirmAutofocusDialog(
    dialogService: TumAetUiDialogService,
    data: ConfirmAutofocusModalData,
    options: Partial<TumAetUiDialogOptions<ConfirmAutofocusDialogData>> = {},
): TumAetUiDialogRef<ConfirmAutofocusModalResult, ConfirmAutofocusModalComponent> {
    // `data` is assigned after the copy, never inside it: it can carry a live TemplateRef (contentRef), which must
    // reach the dialog by reference rather than as a detached clone.
    const config = cloneWith<TumAetUiDialogOptions<ConfirmAutofocusDialogData>, Partial<TumAetUiDialogOptions<ConfirmAutofocusDialogData>>>(
        { size: 'medium', closable: true, closeOnEscape: true, dismissableMask: false, templates: { header: DialogTranslateHeaderComponent } },
        options,
    );
    config.data = cloneWith({ translateText: false, textIsMarkdown: false, confirmDisabled: false, headerKey: data.title, headerParams: data.titleTranslationParams }, data);
    return dialogService.open<ConfirmAutofocusModalComponent, ConfirmAutofocusModalResult, ConfirmAutofocusDialogData>(ConfirmAutofocusModalComponent, config);
}

@Component({
    selector: 'jhi-confirm-modal',
    templateUrl: './confirm-autofocus-modal.component.html',
    imports: [NgTemplateOutlet, TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonDirective],
})
export class ConfirmAutofocusModalComponent {
    private dialogRef = inject<TumAetUiDialogRef<ConfirmAutofocusModalResult>>(TumAetUiDialogRef);
    private dialogConfig = inject<TumAetUiDialogConfig<ConfirmAutofocusDialogData>>(TumAetUiDialogConfig);
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
