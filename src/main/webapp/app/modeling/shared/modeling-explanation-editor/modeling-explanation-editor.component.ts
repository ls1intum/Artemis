import { Component, input, model } from '@angular/core';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/foundation/constants/input.constants';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { onTextEditorTab } from 'app/foundation/util/text.utils';

@Component({
    selector: 'jhi-modeling-explanation-editor',
    templateUrl: './modeling-explanation-editor.component.html',
    styleUrls: ['./modeling-explanation-editor.component.scss'],
    imports: [TranslateDirective, FormsModule],
})
export class ModelingExplanationEditorComponent {
    readOnly = input(false);
    explanation = model<string>();

    readonly maxCharacterCount = MAX_SUBMISSION_TEXT_LENGTH;

    // used in the html template
    protected readonly onTextEditorTab = onTextEditorTab;
}
