import { Component, EventEmitter, Input, Output, input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ProgrammingExerciseInputField } from 'app/exercises/programming/manage/update/programming-exercise-update.helper';

@Component({
    selector: 'jhi-programming-exercise-problem',
    templateUrl: './programming-exercise-problem.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseProblemComponent {
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly ProjectType = ProjectType;
    protected readonly AssessmentType = AssessmentType;
    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly faQuestionCircle = faQuestionCircle;

    @Input({ required: true }) programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();

    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();
    @Output() problemStatementChange = new EventEmitter<string>();

    programmingExercise: ProgrammingExercise;

    @Input()
    get exercise() {
        return this.programmingExercise;
    }

    set exercise(exercise: ProgrammingExercise) {
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }
}
