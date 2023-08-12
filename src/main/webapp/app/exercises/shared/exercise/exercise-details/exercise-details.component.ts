import { Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-exercise-details',
    templateUrl: './exercise-details.component.html',
    styleUrls: ['./exercise-details.component.scss'],
})
export class ExerciseDetailsComponent implements OnInit {
    readonly ExerciseType = ExerciseType;

    @Input() exercise: Exercise;

    programmingExercise?: ProgrammingExercise;
    formattedProblemStatement: SafeHtml;
    formattedGradingInstructions: SafeHtml;
    isExamExercise: boolean;

    constructor(private artemisMarkdown: ArtemisMarkdownService, private accountService: AccountService, public exerciseService: ExerciseService) {}

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.gradingInstructions);
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.programmingExercise = this.exercise as ProgrammingExercise;
        } else {
            // Do not render the markdown here if it is a programming exercises, as ProgrammingExerciseInstructionComponent takes care of that
            this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.problemStatement);
        }
        this.isExamExercise = !!this.exercise.exerciseGroup;
        this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorForExercise(this.exercise);
        this.exercise.isAtLeastEditor = this.accountService.isAtLeastEditorForExercise(this.exercise);
        this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorForExercise(this.exercise);
    }
}
