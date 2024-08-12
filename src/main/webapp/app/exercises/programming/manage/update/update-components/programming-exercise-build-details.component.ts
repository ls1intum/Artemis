import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-build-details',
    templateUrl: './programming-exercise-build-details.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
    standalone: true,
})
export class ProgrammingExerciseBuildDetailsComponent {
    formValid: boolean;
    formValidChanges = new Subject<boolean>();

    // TODO refactor below

    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;
    readonly AssessmentType = AssessmentType;
    readonly MarkdownEditorHeight = MarkdownEditorHeight;

    programmingExercise: ProgrammingExercise;

    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();
    @Output() problemStatementChange = new EventEmitter<string>();

    @Input()
    get exercise() {
        return this.programmingExercise;
    }

    set exercise(exercise: ProgrammingExercise) {
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }

    faQuestionCircle = faQuestionCircle;
}
