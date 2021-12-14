import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, Renderer2, SimpleChanges, ViewChild } from '@angular/core';
import { ApollonEditor, ApollonMode, Assessment, Selection, UMLDiagramType, UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { OtherModelElementCount } from 'app/entities/modeling-submission.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import interact from 'interactjs';
import { MODELING_EDITOR_MAX_HEIGHT, MODELING_EDITOR_MAX_WIDTH, MODELING_EDITOR_MIN_HEIGHT, MODELING_EDITOR_MIN_WIDTH } from 'app/shared/constants/modeling.constants';
import $ from 'jquery';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { faGripLines, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';

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
})
export class ModelingAssessmentComponent implements AfterViewInit, OnDestroy, OnChanges {
    apollonEditor?: ApollonEditor;
    elementFeedback: Map<string, Feedback>; // map element.id --> Feedback
    referencedFeedbacks: Feedback[] = [];
    unreferencedFeedbacks: Feedback[] = [];
    firstCorrectionRoundColor = '#3e8acc';
    secondCorrectionRoundColor = '#ffa561';

    @ViewChild('editorContainer', { static: false }) editorContainer: ElementRef;
    @ViewChild('resizeContainer', { static: false }) resizeContainer: ElementRef;
    @Input() model: UMLModel;
    @Input() explanation: string;
    @Input() highlightedElements: Map<string, string>; // map elementId -> highlight color
    @Input() centeredElementId: string;
    @Input() elementCounts?: OtherModelElementCount[];
    @Input() course?: Course;

    feedbacks: Feedback[];
    @Input() set resultFeedbacks(feedback: Feedback[]) {
        this.feedbacks = feedback;
        this.referencedFeedbacks = this.feedbacks.filter((feedbackElement) => feedbackElement.reference != undefined);
        this.updateApollonAssessments(this.referencedFeedbacks);
    }

    @Input() diagramType?: UMLDiagramType;
    @Input() maxScore: number;
    @Input() maxBonusPoints = 0;
    @Input() totalScore: number;
    @Input() title: string;
    @Input() resizeOptions: { horizontalResize?: boolean; verticalResize?: boolean };
    @Input() readOnly = false;
    @Input() enablePopups = true;
    @Input() displayPoints = true;
    @Input() highlightDifferences: boolean;

    @Output() feedbackChanged = new EventEmitter<Feedback[]>();
    @Output() selectionChanged = new EventEmitter<Selection>();

    // Icons
    faGripLinesVertical = faGripLinesVertical;
    faGripLines = faGripLines;

    constructor(private alertService: AlertService, private renderer: Renderer2, private artemisTranslatePipe: ArtemisTranslatePipe) {}

    ngAfterViewInit(): void {
        if (this.feedbacks) {
            this.referencedFeedbacks = this.feedbacks.filter((feedbackElement) => feedbackElement.reference != undefined);
            this.unreferencedFeedbacks = this.feedbacks.filter(
                (feedbackElement) => feedbackElement.reference == undefined && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED,
            );
        }
        this.initializeApollonEditor();
        if (this.highlightedElements) {
            this.updateHighlightedElements(this.highlightedElements);
        }
        if (this.elementCounts) {
            this.updateElementCounts(this.elementCounts);
        }
        this.applyStateConfiguration();
        if (this.resizeOptions) {
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: this.resizeOptions.horizontalResize && '.draggable-right', bottom: this.resizeOptions.verticalResize, top: false },
                    modifiers: [
                        interact.modifiers!.restrictSize({
                            min: { width: MODELING_EDITOR_MIN_WIDTH, height: MODELING_EDITOR_MIN_HEIGHT },
                            max: { width: MODELING_EDITOR_MAX_WIDTH, height: MODELING_EDITOR_MAX_HEIGHT },
                        }),
                    ],
                    inertia: true,
                })
                .on('resizestart', function (event: any) {
                    event.target.classList.add('card-resizable');
                })
                .on('resizeend', function (event: any) {
                    event.target.classList.remove('card-resizable');
                })
                .on('resizemove', (event: any) => {
                    const target = event.target;
                    if (this.resizeOptions.horizontalResize) {
                        target.style.width = event.rect.width + 'px';
                    }
                    if (this.resizeOptions.verticalResize) {
                        target.style.height = event.rect.height + 'px';
                    }
                });
        }
    }

    ngOnDestroy() {
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.model && changes.model.currentValue && this.apollonEditor) {
            this.apollonEditor!.model = changes.model.currentValue;
            this.handleFeedback();
        }
        if (changes.feedbacks && changes.feedbacks.currentValue && this.model) {
            this.feedbacks = changes.feedbacks.currentValue;
            this.handleFeedback();
            this.applyStateConfiguration();
        }
        if (changes.highlightedElements) {
            this.highlightedElements = changes.highlightedElements.currentValue;

            if (this.apollonEditor) {
                this.applyStateConfiguration();
            }
        }
        if (changes.centeredElementId) {
            if (this.centeredElementId) {
                this.scrollIntoView(this.centeredElementId);
            }
        }
        if (changes.highlightDifferences) {
            this.updateApollonAssessments(this.referencedFeedbacks);
        }
    }

    /**
     * Initializes the Apollon editor after updating the Feedback accordingly. It also subscribes to change
     * events of Apollon an passes them on to parent components.
     */
    private initializeApollonEditor() {
        if (this.apollonEditor) {
            this.apollonEditor.destroy();
        }

        this.handleFeedback();

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            mode: ApollonMode.Assessment,
            readonly: this.readOnly,
            model: this.model,
            type: this.diagramType || UMLDiagramType.ClassDiagram,
            enablePopups: this.enablePopups,
        });
        this.apollonEditor.subscribeToSelectionChange((selection: Selection) => {
            if (this.readOnly) {
                this.selectionChanged.emit(selection);
            }
        });
        if (!this.readOnly) {
            this.apollonEditor.subscribeToAssessmentChange((assessments: Assessment[]) => {
                this.referencedFeedbacks = this.generateFeedbackFromAssessment(assessments);
                this.feedbackChanged.emit(this.referencedFeedbacks);
            });
        }
    }

    private applyStateConfiguration() {
        if (this.highlightedElements) {
            this.updateHighlightedElements(this.highlightedElements);
        }
        if (this.centeredElementId) {
            setTimeout(() => this.scrollIntoView(this.centeredElementId), 0);
        }
    }

    /**
     * Gets the assessments from Apollon and creates/updates the corresponding Feedback entries in the
     * element feedback mapping.
     * Returns an array containing all feedback entries from the mapping.
     */
    private generateFeedbackFromAssessment(assessments: Assessment[]): Feedback[] {
        const newElementFeedback = new Map();
        for (const assessment of assessments) {
            let feedback = this.elementFeedback.get(assessment.modelElementId);
            if (feedback) {
                if (feedback.credits !== assessment.score && feedback.gradingInstruction) {
                    feedback.gradingInstruction = undefined;
                }
                feedback.credits = assessment.score;
                feedback.text = assessment.feedback;
                if (assessment.dropInfo && assessment.dropInfo.instruction?.id) {
                    feedback.gradingInstruction = assessment.dropInfo.instruction;
                }
                if (feedback.gradingInstruction && assessment.dropInfo == undefined) {
                    feedback.gradingInstruction = undefined;
                }
            } else {
                feedback = Feedback.forModeling(assessment.score, assessment.feedback, assessment.modelElementId, assessment.elementType, assessment.dropInfo);
            }
            newElementFeedback.set(assessment.modelElementId, feedback);
        }
        this.elementFeedback = newElementFeedback;
        return [...this.elementFeedback.values()];
    }

    /**
     * Handles (new) feedback by removing invalid feedback, updating the element-feedback mapping and updating
     * the assessments for Apollon accordingly.
     * which is then shown in the score display component.
     * This method is called before initializing Apollon and when the feedback or model is updated.
     */
    private handleFeedback(): void {
        this.referencedFeedbacks = this.removeInvalidFeedback(this.feedbacks);
        this.updateElementFeedbackMapping(this.referencedFeedbacks);
        this.updateApollonAssessments(this.referencedFeedbacks);
    }

    /**
     * Removes feedback elements for which the corresponding model element does not exist in the model anymore.
     * @param feedbacks the list of feedback to filter
     */
    private removeInvalidFeedback(feedbacks: Feedback[]): Feedback[] {
        if (!feedbacks) {
            return feedbacks;
        }
        if (!this.model || !this.model.elements) {
            return [];
        }

        let availableIds: string[] = this.model.elements.map((element) => element.id);
        if (this.model.relationships) {
            availableIds = availableIds.concat(this.model.relationships.map((relationship) => relationship.id));
        }
        return feedbacks.filter((feedback) => availableIds.includes(feedback.referenceId!));
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
    private updateHighlightedElements(newElements: Map<string, string>) {
        if (!newElements) {
            newElements = new Map<string, string>();
        }

        if (this.apollonEditor != undefined) {
            const model: UMLModel = this.apollonEditor!.model;
            for (const element of model.elements) {
                element.highlight = newElements.get(element.id);
            }
            for (const relationship of model.relationships) {
                relationship.highlight = newElements.get(relationship.id);
            }
            this.apollonEditor!.model = model;
        }
    }

    /**
     * Sets the corresponding highlight color in the apollon model of all elements contained in the given element map.
     *
     * @param newElements a map of elementIds -> highlight color
     */
    private updateElementCounts(newElementCounts: OtherModelElementCount[]) {
        if (!newElementCounts) {
            return;
        }

        const elementCountMap = new Map<string, Number>();

        newElementCounts.forEach((elementCount) => elementCountMap.set(elementCount.elementId, elementCount.numberOfOtherElements));

        if (this.apollonEditor != undefined) {
            const model: UMLModel = this.apollonEditor!.model;
            for (const element of model.elements) {
                element.assessmentNote = this.calculateNote(elementCountMap.get(element.id));
            }
            for (const relationship of model.relationships) {
                relationship.assessmentNote = this.calculateNote(elementCountMap.get(relationship.id));
            }
            this.apollonEditor!.model = model;
        }
    }

    private scrollIntoView(elementId: string) {
        const element = this.editorContainer.nativeElement as HTMLElement;
        const matchingElement = $(element).find(`#${elementId}`).get(0);
        if (matchingElement) {
            matchingElement.scrollIntoView({ block: 'center', inline: 'center' });
        }
    }

    /**
     * Converts a given feedback list to Apollon assessments and updates the model of Apollon with the new assessments.
     * @param feedbacks the feedback list to convert and pass on to Apollon
     */
    private updateApollonAssessments(feedbacks: Feedback[]) {
        if (!feedbacks || !this.model) {
            return;
        }

        this.model.assessments = feedbacks.map<Assessment>((feedback) => {
            return {
                modelElementId: feedback.referenceId!,
                elementType: feedback.referenceType! as UMLElementType | UMLRelationshipType,
                score: feedback.credits!,
                feedback: feedback.text || undefined,
                label: this.calculateLabel(feedback),
                labelColor: this.calculateLabelColor(feedback),
                correctionStatus: this.calculateCorrectionStatusForFeedback(feedback),
                dropInfo: this.calculateDropInfo(feedback),
            };
        });
        if (this.apollonEditor) {
            this.apollonEditor!.model = this.model;
        }
    }

    private calculateLabel(feedback: any) {
        const firstCorrectionRoundText = this.artemisTranslatePipe.transform('artemisApp.assessment.diffView.correctionRoundDiffFirst');
        const secondCorrectionRoundText = this.artemisTranslatePipe.transform('artemisApp.assessment.diffView.correctionRoundDiffSecond');
        if (this.highlightDifferences) {
            return feedback.copiedFeedbackId ? firstCorrectionRoundText : secondCorrectionRoundText;
        }
        return undefined;
    }

    private calculateLabelColor(feedback: any) {
        if (this.highlightDifferences) {
            return feedback.copiedFeedbackId ? this.firstCorrectionRoundColor : this.secondCorrectionRoundColor;
        }
        return '';
    }

    private calculateNote(count: Number | undefined) {
        if (count) {
            return this.artemisTranslatePipe.transform('modelingAssessment.impactWarning', { affectedSubmissionsCount: count });
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

    private calculateDropInfo(feedback: Feedback) {
        if (feedback.gradingInstruction) {
            const dropInfo = <DropInfo>{};
            dropInfo.instruction = feedback.gradingInstruction;
            dropInfo.removeMessage = this.artemisTranslatePipe.transform('artemisApp.assessment.messages.removeAssessmentInstructionLink');
            dropInfo.tooltipMessage = this.artemisTranslatePipe.transform('artemisApp.exercise.assessmentInstruction') + feedback!.gradingInstruction!.instructionDescription;
            dropInfo.feedbackHint = this.artemisTranslatePipe.transform('artemisApp.assessment.feedbackHint');
            return dropInfo;
        }

        return undefined;
    }
}
