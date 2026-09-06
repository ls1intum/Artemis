import { Component, input, model } from '@angular/core';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/foundation/constants/input.constants';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TextFieldModule } from '@angular/cdk/text-field';
import { ModelingExplanationSurfaceComponent } from 'app/modeling/shared/modeling-explanation-surface/modeling-explanation-surface.component';

let nextExplanationEditorId = 0;

@Component({
    selector: 'jhi-modeling-explanation-editor',
    templateUrl: './modeling-explanation-editor.component.html',
    styleUrls: ['./modeling-explanation-editor.component.scss'],
    imports: [FormsModule, ArtemisTranslatePipe, TextFieldModule, ModelingExplanationSurfaceComponent],
})
export class ModelingExplanationEditorComponent {
    readOnly = input(false);
    explanation = model<string>();
    labelKey = input('artemisApp.modelingSubmission.explanationText');
    placeholderKey = input<string | undefined>('artemisApp.modelingSubmission.explanationPlaceholder');
    maxCharacterCount = input(MAX_SUBMISSION_TEXT_LENGTH);
    notchWidth = input(104);
    autosizeMaxRows = input(3);
    autosizeMinRows = input(1);
    surfaceMinHeight = input(42);

    protected readonly textareaId = `modeling-explanation-${++nextExplanationEditorId}`;
    protected readonly labelId = `${this.textareaId}-label`;
}
