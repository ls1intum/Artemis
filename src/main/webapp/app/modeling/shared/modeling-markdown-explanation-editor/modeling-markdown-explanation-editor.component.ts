import { Component, ViewEncapsulation, input, model } from '@angular/core';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { TextEditorDomainAction } from 'app/editor/monaco-editor/model/actions/text-editor-domain-action.model';
import { ModelingExplanationSurfaceComponent } from 'app/modeling/shared/modeling-explanation-surface/modeling-explanation-surface.component';

let nextMarkdownExplanationEditorId = 0;

@Component({
    selector: 'jhi-modeling-markdown-explanation-editor',
    templateUrl: './modeling-markdown-explanation-editor.component.html',
    styleUrls: ['./modeling-markdown-explanation-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [MarkdownEditorMonacoComponent, ModelingExplanationSurfaceComponent],
})
export class ModelingMarkdownExplanationEditorComponent {
    markdown = model<string>();
    labelKey = input('artemisApp.modelingExercise.exampleSolutionExplanation');
    notchWidth = input(184);
    domainActions = input<TextEditorDomainAction[]>([]);

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly editorId = `modeling-markdown-explanation-${++nextMarkdownExplanationEditorId}`;
    protected readonly labelId = `${this.editorId}-label`;
}
