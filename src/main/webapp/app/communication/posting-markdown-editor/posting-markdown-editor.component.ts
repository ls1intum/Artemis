import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, forwardRef, inject, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { CommunicationService } from 'app/communication/service/communication.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { PostingEditType } from '../communication.util';
import { MessageEditorComponent } from 'app/communication/message-editor/message-editor.component';
import { canEditMessageVisually } from 'app/communication/message-editor/message-editor-conversion';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';

@Component({
    selector: 'jhi-posting-markdown-editor',
    templateUrl: './posting-markdown-editor.component.html',
    providers: [
        { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PostingMarkdownEditorComponent), multi: true },
        { provide: NG_VALIDATORS, useExisting: forwardRef(() => PostingMarkdownEditorComponent), multi: true },
    ],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    // An idle blank composer stays neutral when Angular marks its required control as touched.
    host: { '[style.border-left-width.px]': '!content()?.trim() ? 0 : undefined', '(focusout)': 'onTouched()' },
    imports: [MessageEditorComponent, TumAetUiButtonDirective, ArtemisTranslatePipe, RedirectToIrisButtonComponent],
})
export class PostingMarkdownEditorComponent implements ControlValueAccessor, Validator {
    private readonly communicationService = inject(CommunicationService);

    readonly maxContentLength = input<number>();
    /** Identifies the message or reply draft independently of its current text. */
    readonly draftIdentity = input<string>();
    readonly isInputLengthDisplayed = input(true);
    readonly suppressNewlineOnEnter = input(true);
    readonly showCloseButton = input(false);
    readonly closeEditor = output<void>();
    readonly isButtonLoading = input(false);
    readonly isFormGroupValid = input(false);
    readonly editType = input<PostingEditType>();
    readonly course = input<Course>();
    /** The parent post supplies the conversation when the all-messages view has none selected. */
    readonly activeConversation = input<ConversationDTO>();
    readonly valueChange = output<void>();
    readonly content = signal<string | undefined>(undefined);
    readonly uploadingFormattedAttachment = signal(false);
    protected readonly supportsFormattedContent = computed(() => canEditMessageVisually(this.content() ?? ''));
    readonly effectiveCourseId = computed(() => this.course()?.id ?? this.communicationService.getCourse()?.id);
    readonly effectiveConversation = computed(() => this.activeConversation() ?? this.communicationService.getCurrentConversation());
    readonly isUpdating = computed(() => this.editType() === PostingEditType.UPDATE);

    private onChange = (_value: string) => {};
    protected onTouched = () => {};
    private validatorChanged = () => {};

    /** Keeps unsupported legacy source intact and prevents submissions during attachment uploads. */
    validate(): ValidationErrors | null {
        if (!this.supportsFormattedContent()) {
            return { unsupportedMessageFormatting: true };
        }
        return this.uploadingFormattedAttachment() ? { attachmentUploading: true } : null;
    }

    registerOnValidatorChange(callback: () => void): void {
        this.validatorChanged = callback;
    }

    formattedUploadStateChanged(uploading: boolean): void {
        this.uploadingFormattedAttachment.set(uploading);
        this.validatorChanged();
    }

    writeValue(value: string | undefined): void {
        const nextValue = value ?? '';
        const wasSupported = this.supportsFormattedContent();
        const resetUpload = this.uploadingFormattedAttachment() && nextValue !== this.content();
        this.content.set(nextValue);
        if (resetUpload) {
            this.uploadingFormattedAttachment.set(false);
        }
        if (resetUpload || wasSupported !== this.supportsFormattedContent()) {
            this.validatorChanged();
        }
    }

    registerOnChange(callback: (value: string) => void): void {
        this.onChange = callback;
    }

    registerOnTouched(callback: () => void): void {
        this.onTouched = callback;
    }

    /** Explicitly discards an incompatible unsent draft; existing messages cannot be cleared this way. */
    discardUnsupportedDraft(): void {
        if (!this.isUpdating() && !this.isButtonLoading() && !this.supportsFormattedContent()) {
            this.updateField('');
        }
    }

    updateField(value: string): void {
        const wasSupported = this.supportsFormattedContent();
        this.content.set(value);
        if (wasSupported !== this.supportsFormattedContent()) {
            this.validatorChanged();
        }
        this.onChange(value);
        this.valueChange.emit();
    }
}
