import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/shared/constants/input.constants';

@Component({
    selector: 'jhi-modeling-explanation-editor',
    templateUrl: './modeling-explanation-editor.component.html',
    styleUrls: ['./modeling-explanation-editor.component.scss'],
})
export class ModelingExplanationEditorComponent {
    @Input()
    readOnly = false;

    @Input()
    explanation: string;

    @Output()
    explanationChange = new EventEmitter();

    readonly maxCharacterCount = MAX_SUBMISSION_TEXT_LENGTH;

    // Add tab to the value of textarea instead of moving to the next element in DOM
    onTextEditorTab(editor: HTMLTextAreaElement, event: Event) {
        event.preventDefault();
        const value = editor.value;
        const start = editor.selectionStart;
        const end = editor.selectionEnd;

        editor.value = value.substring(0, start) + '\t' + value.substring(end);
        editor.selectionStart = editor.selectionEnd = start + 1;
    }

    onExplanationInput(newValue: string) {
        this.explanationChange.emit(newValue);
    }
}
