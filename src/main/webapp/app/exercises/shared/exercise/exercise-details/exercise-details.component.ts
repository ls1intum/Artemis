import { Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-exercise-details',
    templateUrl: './exercise-details.component.html',
    styleUrls: ['./exercise-details.component.scss'],
})
export class ExerciseDetailsComponent implements OnInit {
    @Input() exercise: Exercise;

    programmingExercise?: ProgrammingExercise;
    AssessmentType = AssessmentType;
    ExerciseType = ExerciseType;
    formattedProblemStatement: SafeHtml;
    formattedGradingInstructions: SafeHtml;
    isExamExercise: boolean;

    constructor(private artemisMarkdown: ArtemisMarkdownService, private accountService: AccountService) {}
    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.gradingInstructions);
        this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.problemStatement);
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.programmingExercise = this.exercise as ProgrammingExercise;
        }
        this.isExamExercise = !!this.exercise.exerciseGroup;
        this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorForExercise(this.exercise);
        this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorForExercise(this.exercise);
    }
}
