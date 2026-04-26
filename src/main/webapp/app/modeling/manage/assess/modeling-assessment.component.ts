import { AfterViewInit, Component, OnDestroy, effect, inject, input, output } from '@angular/core';
import { ApollonEditor, ApollonMode, Assessment, UMLDiagramType, UMLModel } from '@tumaet/apollon';
import { captureException } from '@sentry/angular';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
    FeedbackType,
} from 'app/assessment/shared/entities/feedback.model';
import { ModelElementCount } from 'app/modeling/shared/entities/modeling-submission.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Course } from 'app/core/course/shared/entities/course.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { ModelingComponent } from 'app/modeling/shared/modeling/modeling.component';
import { filterInvalidFeedback } from 'app/modeling/manage/assess/modeling-assessment.util';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ModelingExplanationEditorComponent } from 'app/modeling/shared/modeling-explanation-editor/modeling-explanation-editor.component';

export interface DropInfo {
    instruction: GradingInstruction;
    tooltipMessage: string;
    removeMessage: string;
    feedbackHint: string;
}

@Component({
    selector: 'jhi-modeling-assessment',
    templateUrl: './modeling-assessment.component.html',
    styleUrls: ['./modeling-assessment.component.scss'],
    imports: [ScoreDisplayComponent, FaIconComponent, ModelingExplanationEditorComponent],
})
export class ModelingAssessmentComponent extends ModelingComponent implements AfterViewInit, OnDestroy {
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);

    maxScore = input<number>(0);
    maxBonusPoints = input(0);
    totalScore = input<number>(0);
    title = input<string>();
    enablePopups = input(true);
    displayPoints = input(true);
    highlightDifferences = input<boolean>();
    resultFeedbacks = input<Feedback[]>();

    feedbackChanged = output<Feedback[]>();
    selectedElementIdsChanged = output<string[]>();

    highlightedElements = input<Map<string, string> | undefined>(undefined); // map elementId -> highlight color
    elementCounts = input<ModelElementCount[]>();
    course = input<Course>();

    elementFeedback: Map<string, Feedback> = new Map<string, Feedback>(); // map element.id --> Feedback
    private shownInApollon: Map<string, string> = new Map<string, string>(); // map element.id --> feedback content last passed to Apollon
    referencedFeedbacks: Feedback[] = [];
    unreferencedFeedbacks: Feedback[] = [];
    firstCorrectionRoundColor = '#3e8acc';
    secondCorrectionRoundColor = '#ffa561';

    private modelChangeSubscription?: number;
    private assessmentSelectionSubscription?: number;

    constructor() {
        super();
        effect(() => {
            // we register signals for effect by calling the getters
            // anytime the signal changes value, effect is triggered to run this.runHighlightUpdate()
            this.highlightedElements();
            this.highlightDifferences();

            if (!this.apollonEditor) {
                return;
            }

            this.runHighlightUpdate();
        });
        effect(() => {
            const incoming = this.resultFeedbacks();

            if (!incoming || !this.apollonEditor) {
                return;
            }

            this.referencedFeedbacks = incoming.filter((feedbackElement) => feedbackElement.reference != undefined);
            this.updateElementFeedbackMapping(this.referencedFeedbacks);
            this.updateApollonAssessments(this.referencedFeedbacks);
        });

        effect(() => {
            const model = this.umlModel();

            if (!model || !this.apollonEditor) {
                return;
            }

            try {
                this.apollonEditor.model = model;
                this.handleFeedback();
            } catch (err) {
                // Editor may not be fully initialized yet or already destroyed
                captureException(err);
            }
        });
    }

    async ngAfterViewInit(): Promise<void> {
        const resultFeedbacks = this.resultFeedbacks();
        if (resultFeedbacks !== undefined) {
            this.referencedFeedbacks = resultFeedbacks.filter((feedbackElement) => feedbackElement.reference != undefined);
            this.unreferencedFeedbacks = resultFeedbacks.filter(
                (feedbackElement) => feedbackElement.reference == undefined && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED,
            );
        }
        this.initializeApollonEditor();
        const highlightedElements = this.highlightedElements();
        if (highlightedElements) {
            await this.updateHighlightedElements(highlightedElements);
        }
        const elementCounts = this.elementCounts();
        if (elementCounts) {
            await this.updateElementCounts(elementCounts);
        }
        // Ensure assessments are added after editor initialization
        await this.updateApollonAssessments(this.referencedFeedbacks);
        await this.applyStateConfiguration();
        this.setupInteract();
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        if (this.apollonEditor) {
            if (this.modelChangeSubscription !== undefined) {
                this.apollonEditor.unsubscribe(this.modelChangeSubscription);
            }
            if (this.assessmentSelectionSubscription !== undefined) {
                this.apollonEditor.unsubscribe(this.assessmentSelectionSubscription);
            }
            this.apollonEditor.destroy();
        }
    }

    /**
     * update if highlighted stuff has been changed
     * @private
     */
    private async runHighlightUpdate() {
        await this.updateApollonAssessments(this.referencedFeedbacks);
        await this.applyStateConfiguration();
    }

    /**
     * Initializes the Apollon editor after updating the Feedback accordingly. It also subscribes to change
     * events of Apollon and passes them on to parent components.
     */
    private initializeApollonEditor() {
        this.handleFeedback();

        this.apollonEditor = new ApollonEditor(this.editorContainer()!.nativeElement, {
            mode: ApollonMode.Assessment,
            readonly: this.readOnly(),
            model: this.umlModel(),
            type: this.diagramType() || UMLDiagramType.ClassDiagram,
            enablePopups: this.enablePopups(),
        });

        this.modelChangeSubscription = this.apollonEditor.subscribeToModelChange((state) => {
            if (!this.readOnly()) {
                const assessmentsArray = Object.values(state.assessments);
                this.referencedFeedbacks = this.generateFeedbackFromAssessment(assessmentsArray);
                this.feedbackChanged.emit(this.referencedFeedbacks);
            }
        });

        if (this.readOnly()) {
            this.assessmentSelectionSubscription = this.apollonEditor.subscribeToAssessmentSelection((selections) => this.selectedElementIdsChanged.emit(selections));
        }
    }

    private async applyStateConfiguration() {
        const highlightedElements = this.highlightedElements();
        if (highlightedElements) {
            await this.updateHighlightedElements(highlightedElements);
        }
    }

    /**
     * Gets the assessments from Apollon and creates/updates the corresponding Feedback entries in the
     * element feedback mapping.
     * Returns an array containing all feedback entries from the mapping.
     */
    generateFeedbackFromAssessment(assessments: Assessment[]): Feedback[] {
        for (const assessment of assessments) {
            // Apollon stores the GradingInstruction flat on dropInfo (not nested under dropInfo.instruction)
            // Support both: dropInfo.instruction (expected shape) and dropInfo directly (actual Apollon shape)
            const dropInfo = assessment.dropInfo as any;
            const instruction = dropInfo?.instruction ?? (dropInfo?.id ? dropInfo : undefined);
            let feedback = this.elementFeedback.get(assessment.modelElementId);
            if (feedback) {
                if (feedback.credits !== assessment.score && feedback.gradingInstruction) {
                    feedback.gradingInstruction = undefined;
                }
                feedback.credits = assessment.score;
                if (Feedback.isFeedbackSuggestion(feedback)) {
                    const alreadyAdapted = feedback.text?.startsWith(FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER);
                    if (alreadyAdapted) {
                        // Title is already stored in text; only update detailText with Apollon's new content
                        if (assessment.feedback !== undefined) {
                            feedback.detailText = assessment.feedback;
                        }
                    } else {
                        const lastShown = this.shownInApollon.get(assessment.modelElementId);
                        if (assessment.feedback !== undefined && lastShown !== undefined && assessment.feedback !== lastShown) {
                            // Instructor changed the content — preserve original title in text, update detailText
                            const originalTitle = this.stripSuggestionPrefix(feedback.text ?? '');
                            feedback.text = FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER + originalTitle;
                            feedback.detailText = assessment.feedback;
                            this.shownInApollon.set(assessment.modelElementId, assessment.feedback);
                        }
                    }
                    // else: auto-emit or unchanged content, keep original ACCEPTED/IDENTIFIER prefix
                } else {
                    feedback.text = assessment.feedback;
                }
                if (instruction?.id) {
                    feedback.gradingInstruction = instruction;
                }
                if (feedback.gradingInstruction && assessment.dropInfo == undefined) {
                    feedback.gradingInstruction = undefined;
                }
            } else {
                // elementFeedback is pre-populated by updateElementFeedbackMapping; a missing entry means
                // Apollon emitted this element before we processed it — create and register it now
                feedback = Feedback.forModeling(assessment.score, assessment.feedback, assessment.modelElementId, assessment.elementType, assessment.dropInfo as DropInfo);
                this.elementFeedback.set(assessment.modelElementId, feedback);
            }
        }
        // Return only the feedbacks currently reported by Apollon (not stale entries)
        return assessments.map((a) => this.elementFeedback.get(a.modelElementId)!).filter(Boolean);
    }

    /**
     * Handles (new) feedback by removing invalid feedback, updating the element-feedback mapping and updating
     * the assessments for Apollon accordingly.
     * which is then shown in the score display component.
     * This method is called before initializing Apollon and when the feedback or model is updated.
     */
    private handleFeedback(): void {
        const feedbacks = this.resultFeedbacks();
        if (feedbacks !== undefined) {
            this.referencedFeedbacks = filterInvalidFeedback(feedbacks, this.umlModel());
            this.updateElementFeedbackMapping(this.referencedFeedbacks);
            this.updateApollonAssessments(this.referencedFeedbacks);
        }
    }

    /**
     * Updates the mapping of elementIds to Feedback elements. This should be called after getting the
     * (updated) Feedback list from the server.
     *
     * @param feedbacks new Feedback elements to insert
     */
    private updateElementFeedbackMapping(feedbacks: Feedback[]) {
        if (!this.elementFeedback) {
            this.elementFeedback = new Map();
        }
        if (!feedbacks) {
            return;
        }
        for (const feedback of feedbacks) {
            this.elementFeedback.set(feedback.referenceId!, feedback);
        }
    }

    /**
     * Sets the corresponding highlight color in the apollon model of all elements contained in the given element map.
     *
     * @param newElements a map of elementIds -> highlight color
     */
    private async updateHighlightedElements(newElements: Map<string, string>) {
        if (!newElements) {
            newElements = new Map<string, string>();
        }

        if (this.apollonEditor != undefined) {
            const model: UMLModel = this.apollonEditor.model;
            for (const node of model.nodes) {
                const highlight = newElements.get(node.id);
                (node as any).highlight = highlight;
                if (node.data) {
                    (node.data as Record<string, unknown>).highlight = highlight;
                }
            }
            for (const edge of model.edges) {
                const highlight = newElements.get(edge.id);
                (edge as any).highlight = highlight;
                if (edge.data) {
                    (edge.data as Record<string, unknown>).highlight = highlight;
                }
            }
            this.apollonEditor.model = model;
        }
    }

    /**
     * Sets the corresponding highlight color in the apollon model of all elements contained in the given element map.
     *
     * @param newElementCounts a map of elementIds -> highlight color
     */
    private async updateElementCounts(newElementCounts: ModelElementCount[]) {
        if (!newElementCounts) {
            return;
        }

        const elementCountMap = new Map<string, number>();

        newElementCounts.forEach((elementCount) => elementCountMap.set(elementCount.elementId, elementCount.numberOfOtherElements));

        if (this.apollonEditor != undefined) {
            const model: UMLModel = this.apollonEditor.model;
            for (const node of model.nodes) {
                node.data.assessmentNote = this.calculateNote(elementCountMap.get(node.id));
            }
            for (const edge of model.edges) {
                edge.data.assessmentNote = this.calculateNote(elementCountMap.get(edge.id));
            }
            this.apollonEditor.model = model;
        }
    }

    /**
     * Converts a given feedback list to Apollon assessments and updates the model of Apollon with the new assessments.
     * @param feedbacks the feedback list to convert and pass on to Apollon
     */
    private async updateApollonAssessments(feedbacks: Feedback[]) {
        const umlModel = this.umlModel();
        if (!feedbacks || !umlModel) {
            return;
        }

        feedbacks.forEach((feedback) => {
            const feedbackContent = Feedback.isFeedbackSuggestion(feedback) ? (feedback.detailText ?? '') : (feedback.text ?? '');
            this.shownInApollon.set(feedback.referenceId!, feedbackContent);
            const newAssessment: Assessment = {
                modelElementId: feedback.referenceId!,
                elementType: feedback.referenceType!,
                score: feedback.credits ?? 0,
                feedback: feedbackContent,
                label: this.calculateLabel(feedback),
                labelColor: this.calculateLabelColor(feedback),
                correctionStatus: this.calculateCorrectionStatusForFeedback(feedback),
                dropInfo: this.calculateDropInfo(feedback),
            };
            if (!umlModel.assessments) {
                umlModel.assessments = {} as any;
            }
            umlModel.assessments[feedback.referenceId!] = newAssessment;
            if (this.apollonEditor) {
                try {
                    this.apollonEditor.addOrUpdateAssessment(newAssessment);
                } catch (error) {
                    captureException(error);
                    // Fall back to reassigning the model so assessments are still reflected in degraded environments (e.g., tests).
                    this.apollonEditor.model = umlModel;
                }
            }
        });

        // Refresh Apollon rendering to display assessment status icons (check/cross) immediately.
        // Reassigning the model forces internal store sync and visual refresh without waiting for user interaction.
        if (this.apollonEditor) {
            const currentModel = this.apollonEditor.model;
            this.apollonEditor.model = { ...currentModel };
        }
    }

    private calculateLabel(feedback: any) {
        const firstCorrectionRoundText = this.artemisTranslatePipe.transform('artemisApp.assessment.diffView.correctionRoundDiffFirst');
        const secondCorrectionRoundText = this.artemisTranslatePipe.transform('artemisApp.assessment.diffView.correctionRoundDiffSecond');
        if (this.highlightDifferences()) {
            return feedback.copiedFeedbackId ? firstCorrectionRoundText : secondCorrectionRoundText;
        }
        return undefined;
    }

    private calculateLabelColor(feedback: any) {
        if (this.highlightDifferences()) {
            return feedback.copiedFeedbackId ? this.firstCorrectionRoundColor : this.secondCorrectionRoundColor;
        }
        return '';
    }

    private calculateNote(count: number | undefined) {
        if (count) {
            return this.artemisTranslatePipe.transform('artemisApp.modelingAssessment.impactWarning', { affectedSubmissionsCount: count });
        }

        return undefined;
    }

    private calculateCorrectionStatusForFeedback(feedback: Feedback) {
        let correctionStatusDescription = feedback.correctionStatus
            ? this.artemisTranslatePipe.transform('artemisApp.exampleSubmission.feedback.' + feedback.correctionStatus)
            : feedback.correctionStatus;
        if (feedback.correctionStatus && feedback.correctionStatus !== 'CORRECT') {
            // Adding a missing warning icon to the translation strings of incorrect feedbacks.
            correctionStatusDescription += ' ⚠️';
        }
        let correctionStatus: 'CORRECT' | 'INCORRECT' | 'NOT_VALIDATED';
        switch (feedback.correctionStatus) {
            case 'CORRECT':
                correctionStatus = 'CORRECT';
                break;
            case undefined:
                correctionStatus = 'NOT_VALIDATED';
                break;
            default:
                correctionStatus = 'INCORRECT';
        }

        return {
            description: correctionStatusDescription,
            status: correctionStatus,
        };
    }

    private stripSuggestionPrefix(text: string): string {
        for (const prefix of [FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER]) {
            if (text.startsWith(prefix)) {
                return text.slice(prefix.length);
            }
        }
        return text;
    }

    private calculateDropInfo(feedback: Feedback) {
        if (feedback.gradingInstruction) {
            // Apollon stores and emits dropInfo as a flat object (the GradingInstruction itself),
            // not nested under dropInfo.instruction — so we pass the instruction directly as dropInfo.
            return feedback.gradingInstruction;
        }

        return undefined;
    }
}
