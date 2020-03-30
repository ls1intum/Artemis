import { Component, AfterViewInit, HostBinding, Input, Output, EventEmitter, ViewChild, ElementRef, HostListener } from '@angular/core';
import { TextBlock } from 'app/entities/text-block.model';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-textblock-feedback-editor',
    templateUrl: './textblock-feedback-editor.component.html',
    styleUrls: ['./textblock-feedback-editor.component.scss'],
})
export class TextblockFeedbackEditorComponent implements AfterViewInit {
    @Input() textBlock: TextBlock;
    @Input() feedback: Feedback = new Feedback();
    @Output() feedbackChange = new EventEmitter<Feedback>();
    @Output() close = new EventEmitter<void>();
    @Output() onFocus = new EventEmitter<void>();
    @ViewChild('detailText') textareaRef: ElementRef;
    private textareaElement: HTMLTextAreaElement;

    @HostBinding('class.alert') @HostBinding('class.alert-dismissible') readonly classes = true;
    @HostBinding('class.alert-secondary') get setNeutralFeedbackClass(): boolean {
        return this.feedback.credits === 0;
    }
    @HostBinding('class.alert-success') get setPositiveFeedbackClass(): boolean {
        return this.feedback.credits > 0;
    }
    @HostBinding('class.alert-danger') get setNegativeFeedbackClass(): boolean {
        return this.feedback.credits < 0;
    }

    ngAfterViewInit(): void {
        this.textareaElement = this.textareaRef.nativeElement as HTMLTextAreaElement;
    }

    textareaAutogrow(): void {
        this.textareaElement.style.height = '0px';
        this.textareaElement.style.height = `${this.textareaElement.scrollHeight}px`;
    }

    get canDismiss(): boolean {
        return this.feedback.credits === 0 && (this.feedback.detailText || '').length === 0;
    }

    inFocus(): void {
        this.onFocus.emit();
    }

    dismiss(): void {
        if (this.canDismiss) {
            this.close.emit();
        }
    }

    focus(): void {
        this.textareaElement.focus();
    }

    onScoreClick(event: MouseEvent): void {
        event.preventDefault();
    }

    didChange(): void {
        this.feedbackChange.emit(this.feedback);
    }
}
