import { Component, computed, effect, inject, input, model } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ExpandableSectionComponent } from '../expandable-section/expandable-section.component';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { SecureLinkDirective } from 'app/assessment/manage/secure-link.directive';

@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
    imports: [
        ExpandableSectionComponent,
        StructuredGradingInstructionsAssessmentLayoutComponent,
        ProgrammingExerciseInstructionComponent,
        SecureLinkDirective,
        ButtonComponent,
        TranslateDirective,
        ModelingEditorComponent,
    ],
})
export class AssessmentInstructionsComponent {
    private markdownService = inject(ArtemisMarkdownService);

    readonly exercise = input.required<Exercise>();

    readonly isAssessmentTraining = input(false);
    readonly showAssessmentInstructions = input(true);
    readonly readOnly = input<boolean>();
    // For programming exercises we hand over the participation or use the template participation
    readonly programmingParticipation = input<ProgrammingExerciseStudentParticipation>();
    readonly gradingCriteria = model<GradingCriterion[]>();

    readonly ExerciseType = ExerciseType;

    readonly problemStatement = computed(() => this.markdownService.safeHtmlForMarkdown(this.exercise().problemStatement));

    readonly gradingInstructions = computed(() => {
        const exercise = this.exercise();
        return exercise.gradingInstructions ? this.markdownService.safeHtmlForMarkdown(exercise.gradingInstructions) : undefined;
    });

    readonly programmingExercise = computed(() => {
        const exercise = this.exercise();
        return exercise.type === ExerciseType.PROGRAMMING ? (exercise as ProgrammingExercise) : undefined;
    });

    readonly sampleSolutionModel = computed<UMLModel | undefined>(() => {
        const exercise = this.exercise();
        if (exercise.type === ExerciseType.MODELING) {
            const modelingExercise = exercise as ModelingExercise;
            return modelingExercise.exampleSolutionModel ? JSON.parse(modelingExercise.exampleSolutionModel) : undefined;
        }
        return undefined;
    });

    readonly sampleSolutionDiagramType = computed<UMLDiagramType | undefined>(() => {
        const exercise = this.exercise();
        if (exercise.type === ExerciseType.MODELING) {
            return (exercise as ModelingExercise).diagramType;
        }
        return undefined;
    });

    readonly sampleSolutionExplanation = computed<SafeHtml | undefined>(() => {
        const exercise = this.exercise();
        let sampleSolutionMarkdown: string | undefined;

        switch (exercise.type) {
            case ExerciseType.MODELING:
                sampleSolutionMarkdown = (exercise as ModelingExercise).exampleSolutionExplanation;
                break;
            case ExerciseType.TEXT:
                sampleSolutionMarkdown = (exercise as TextExercise).exampleSolution;
                break;
            case ExerciseType.FILE_UPLOAD:
                sampleSolutionMarkdown = (exercise as FileUploadExercise).exampleSolution;
                break;
        }

        return sampleSolutionMarkdown ? this.markdownService.safeHtmlForMarkdown(sampleSolutionMarkdown) : undefined;
    });

    constructor() {
        effect(() => {
            const exercise = this.exercise();
            this.gradingCriteria.set(exercise.gradingCriteria);
        });
    }
}
