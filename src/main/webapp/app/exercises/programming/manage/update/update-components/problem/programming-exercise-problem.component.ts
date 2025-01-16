import { Component, EventEmitter, Input, Output, input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ProgrammingExerciseInputField } from 'app/exercises/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbAlert } from '@ng-bootstrap/ng-bootstrap';
import { CompetencySelectionComponent } from 'app/shared/competency-selection/competency-selection.component';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-programming-exercise-problem',
    templateUrl: './programming-exercise-problem.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
    imports: [
        TranslateDirective,
        NgbAlert,
        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseEditableInstructionComponent,
        CompetencySelectionComponent,
        FormsModule,
        ArtemisTranslatePipe,
    ],
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
