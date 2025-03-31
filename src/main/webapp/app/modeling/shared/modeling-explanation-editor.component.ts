import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/shared/constants/input.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { onTextEditorTab } from 'app/shared/util/text.utils';

@Component({
    selector: 'jhi-modeling-explanation-editor',
    templateUrl: './modeling-explanation-editor.component.html',
    styleUrls: ['./modeling-explanation-editor.component.scss'],
    imports: [TranslateDirective, FormsModule],
})
export class ModelingExplanationEditorComponent {
    @Input() readOnly = false;
    @Input() explanation: string;
    @Output() explanationChange = new EventEmitter();

    readonly maxCharacterCount = MAX_SUBMISSION_TEXT_LENGTH;

    // used in the html template
    protected readonly onTextEditorTab = onTextEditorTab;

    onExplanationInput(newValue: string) {
        this.explanationChange.emit(newValue);
    }
}
