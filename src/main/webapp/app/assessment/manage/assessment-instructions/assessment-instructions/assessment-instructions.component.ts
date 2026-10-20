import { Component, computed, effect, inject, input, model, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { CollapsedPanelRailItem } from 'app/shared-ui/components/collapsed-panel-rail/collapsed-panel-rail.component';
import { faCircleInfo, faFileLines, faGraduationCap, faLightbulb, faListCheck } from '@fortawesome/free-solid-svg-icons';
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
import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** The sections of the instructions. As tabs, each is one tab. */
export type AssessmentInstructionsTabId = 'assessmentNote' | 'criteria' | 'problemStatement' | 'sampleSolution' | 'training';

export interface AssessmentInstructionsTab {
    id: AssessmentInstructionsTabId;
    labelKey: string;
    icon: IconProp;
}

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
        TumAetUiTagComponent,
        ArtemisTranslatePipe,
        NgTemplateOutlet,
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
    /**
     * Shows each section as a panel of a tab instead of a list of sections that open and close, as the tabs of the exercise details do. The
     * page draws the tabs in the header of its panel with `jhi-assessment-instructions-tabs`. A section the exercise has nothing for has no tab.
     */
    readonly tabbed = input(false);
    readonly readOnly = input<boolean>();
    // For programming exercises we hand over the participation or use the template participation
    readonly programmingParticipation = input<ProgrammingExerciseStudentParticipation>();
    readonly gradingCriteria = model<GradingCriterion[]>();

    readonly ExerciseType = ExerciseType;

    /** The tabs this exercise has, in the order the sections have: the note, the criteria, the problem statement, the sample solution. */
    readonly tabs = computed<AssessmentInstructionsTab[]>(() => {
        const tabs: AssessmentInstructionsTab[] = [];
        if (this.showAssessmentInstructions() && this.gradingInstructions()) {
            tabs.push({ id: 'assessmentNote', labelKey: 'artemisApp.assessmentInstructions.tabs.assessmentNote', icon: faCircleInfo });
        }
        if (this.showAssessmentInstructions() && (this.gradingCriteria()?.length ?? 0) > 0) {
            tabs.push({ id: 'criteria', labelKey: 'artemisApp.assessmentInstructions.tabs.criteria', icon: faListCheck });
        }
        tabs.push({ id: 'problemStatement', labelKey: 'artemisApp.assessmentInstructions.tabs.problemStatement', icon: faFileLines });
        tabs.push({ id: 'sampleSolution', labelKey: 'artemisApp.assessmentInstructions.tabs.sampleSolution', icon: faLightbulb });
        if (this.isAssessmentTraining()) {
            tabs.push({ id: 'training', labelKey: 'artemisApp.assessmentInstructions.tabs.training', icon: faGraduationCap });
        }
        return tabs;
    });

    /** What stands for the instructions once their panel is collapsed: the icon of each tab, which opens the panel at that tab. */
    // A signal an effect fills, not a computed: the page reads it in its own template, before the exercise input of this component is set, and reading that input too early throws
    readonly railItems = signal<CollapsedPanelRailItem[]>([]);

    private readonly chosenTab = signal<AssessmentInstructionsTabId | undefined>(undefined);

    /** The open tab: the one the tutor chose, else the criteria, which the tutor works with, else the first one. */
    readonly selectedTab = computed<AssessmentInstructionsTabId>(() => {
        const tabs = this.tabs();
        const chosen = this.chosenTab();
        if (chosen && tabs.some((tab) => tab.id === chosen)) {
            return chosen;
        }
        return (tabs.find((tab) => tab.id === 'criteria') ?? tabs[0]).id;
    });

    selectTab(value: number | string | undefined): void {
        const tab = this.tabs().find((candidate) => candidate.id === value);
        if (tab) {
            this.chosenTab.set(tab.id);
        }
    }

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
        effect(() => this.railItems.set(this.tabs().map((tab) => ({ id: tab.id, icon: tab.icon, labelKey: tab.labelKey }))));
    }
}
