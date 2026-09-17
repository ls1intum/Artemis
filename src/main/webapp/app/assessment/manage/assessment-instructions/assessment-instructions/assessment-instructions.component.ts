import { Component, computed, effect, inject, input, model } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel, importDiagram } from '@tumaet/apollon';
import { SecureLinkDirective } from 'app/assessment/manage/secure-link.directive';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { parseJson } from 'app/foundation/util/json.util';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExpandableSectionComponent } from '../expandable-section/expandable-section.component';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TumUiTagComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
    styleUrl: './assessment-instructions.component.scss',
    imports: [
        ExpandableSectionComponent,
        StructuredGradingInstructionsAssessmentLayoutComponent,
        ProgrammingExerciseInstructionComponent,
        SecureLinkDirective,
        ButtonComponent,
        TranslateDirective,
        ModelingEditorComponent,
        TumUiTagComponent,
        ArtemisTranslatePipe,
    ],
})
export class AssessmentInstructionsComponent {
    private markdownService = inject(ArtemisMarkdownService);
    private readonly selectionService = inject(GradingInstructionSelectionService);

    readonly exercise = input.required<Exercise>();

    readonly isAssessmentTraining = input(false);
    readonly showAssessmentInstructions = input(true);
    /** Drops the frame and heading, for a host whose surrounding card already provides both. */
    readonly embeddedInEditorChrome = input(false);
    readonly readOnly = input<boolean>();
    // For programming exercises we hand over the participation or use the template participation
    readonly programmingParticipation = input<ProgrammingExerciseStudentParticipation>();
    readonly gradingCriteria = model<GradingCriterion[]>();

    readonly ExerciseType = ExerciseType;

    readonly problemStatement = computed(() => this.markdownService.safeHtmlForMarkdown(this.exercise().problemStatement));

    // Instructions can only be ticked off while an editable feedback list is mounted to receive them.
    readonly selectable = computed(() => !this.readOnly() && this.selectionService.isSelectable());

    // How many of all structured grading instructions of this exercise are currently applied, and how many exist.
    readonly appliedInstructionCount = computed(() => {
        const applied = this.selectionService.appliedInstructionIds();
        const instructions = (this.gradingCriteria() ?? []).flatMap((criterion) => criterion.structuredGradingInstructions ?? []);
        return { applied: instructions.filter((instruction) => instruction.id !== undefined && applied.has(instruction.id)).length, total: instructions.length };
    });

    readonly gradingInstructions = computed(() => {
        const exercise = this.exercise();
        return exercise.gradingInstructions ? this.markdownService.safeHtmlForMarkdown(exercise.gradingInstructions) : undefined;
    });

    readonly programmingExercise = computed<ProgrammingExercise | undefined>(() => {
        const exercise = this.exercise();
        return exercise.type === ExerciseType.PROGRAMMING ? exercise : undefined;
    });

    readonly sampleSolutionModel = computed<UMLModel | undefined>(() => {
        const exercise = this.exercise();
        if (exercise.type === ExerciseType.MODELING) {
            const modelingExercise = exercise as ModelingExercise;
            return modelingExercise.exampleSolutionModel ? importDiagram(parseJson(modelingExercise.exampleSolutionModel)) : undefined;
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
