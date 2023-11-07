import { Component, Input, ViewChild } from '@angular/core';
import { BuildAction, ProgrammingExercise, ProjectType, ScriptAction } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';

@Component({
    selector: 'jhi-programming-exercise-custom-build-plan',
    templateUrl: './programming-exercise-custom-build-plan.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseCustomBuildPlanComponent {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    protected readonly ProjectType = ProjectType;

    code: string = '#!/bin/bash\n\n# Add your custom build plan here\n\nexit 0';
    active?: BuildAction = undefined;

    private _editor?: AceEditorComponent;

    @ViewChild('editor', { static: false }) set editor(value: AceEditorComponent) {
        this._editor = value;
        if (this._editor) {
            this.setupEditor();
            this._editor.setText(this.code);
        }
    }

    get editor(): AceEditorComponent | undefined {
        return this._editor;
    }

    faQuestionCircle = faQuestionCircle;

    protected getActionScript(action: string): string {
        const foundAction: BuildAction | undefined = this.programmingExercise.windFile?.actions.find((a) => a.name === action);
        if (foundAction && foundAction instanceof ScriptAction) {
            return (foundAction as ScriptAction).script;
        }
        return '';
    }

    isScriptAction(action: BuildAction): boolean {
        return action instanceof ScriptAction;
    }

    changeActiveAction(action: string): void {
        if (!this.programmingExercise.windFile) {
            return;
        }

        this.code = this.getActionScript(action);
        this.active = this.programmingExercise.windFile.actions.find((a) => a.name === action);
        if (this.needsEditor() && this.editor) {
            this.editor.setText(this.code);
        }
    }

    protected needsEditor(): boolean {
        return this.active instanceof ScriptAction;
    }

    deleteAction(action: string): void {
        if (this.programmingExercise.windFile) {
            this.programmingExercise.windFile.actions = this.programmingExercise.windFile.actions.filter((a) => a.name !== action);
            if (this.active?.name === action) {
                this.active = undefined;
                this.code = '';
            }
        }
    }

    addAction(action: string): void {
        if (this.programmingExercise.windFile) {
            const newAction = new ScriptAction();
            newAction.script = '#!/bin/bash\n\n# Add your custom build plan action here\n\nexit 0';
            newAction.name = action;
            newAction.run_always = false;
            this.programmingExercise.windFile.actions.push(newAction);
            this.changeActiveAction(action);
        }
    }

    codeChanged(code: string): void {
        if (this.active instanceof ScriptAction) {
            (this.active as ScriptAction).script = code;
        }
    }

    /**
     * Sets up an ace editor for the template or solution file.
     */
    setupEditor(): void {
        if (!this._editor) {
            return;
        }
        this._editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: 20,
            showPrintMargin: false,
            readOnly: false,
            highlightActiveLine: false,
            highlightGutterLine: false,
            minLines: 20,
            mode: 'ace/mode/sh',
        });
        this._editor.getEditor().renderer.setOptions({
            showFoldWidgets: false,
        });
    }
}
