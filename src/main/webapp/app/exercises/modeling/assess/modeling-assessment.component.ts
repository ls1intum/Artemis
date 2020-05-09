import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, Renderer2, SimpleChanges, ViewChild } from '@angular/core';
import { ApollonEditor, ApollonMode, Assessment, Selection, UMLDiagramType, UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { AlertService } from 'app/core/alert/alert.service';
import interact from 'interactjs';
import { Feedback } from 'app/entities/feedback.model';
import * as $ from 'jquery';

@Component({
    selector: 'jhi-modeling-assessment',
    templateUrl: './modeling-assessment.component.html',
    styleUrls: ['./modeling-assessment.component.scss'],
})
export class ModelingAssessmentComponent implements AfterViewInit, OnDestroy, OnChanges {
    apollonEditor: ApollonEditor | null = null;
    elementFeedback: Map<string, Feedback>; // map element.id --> Feedback
    totalScore = 0;

    @ViewChild('editorContainer', { static: false }) editorContainer: ElementRef;
    @ViewChild('resizeContainer', { static: false }) resizeContainer: ElementRef;
    @Input() model: UMLModel;
    @Input() highlightedElements: Map<string, string>; // map elementId -> highlight color
    @Input() centeredElementId: string;
    @Input() feedbacks: Feedback[] = [];
    @Input() diagramType: UMLDiagramType;
    @Input() maxScore: number;
    @Input() title: string;
    @Input() resizeOptions: { initialWidth: string; maxWidth?: number };
    @Input() readOnly = false;
    @Input() enablePopups = true;
    @Input() displayPoints = true;
    @Output() feedbackChanged = new EventEmitter<Feedback[]>();
    @Output() selectionChanged = new EventEmitter<Selection>();

    constructor(private jhiAlertService: AlertService, private renderer: Renderer2) {}

    ngAfterViewInit(): void {
        this.initializeApollonEditor();
        if (this.highlightedElements) {
            this.updateHighlightedElements(this.highlightedElements);
        }
        this.applyStateConfiguration();
        if (this.resizeOptions) {
            if (this.resizeOptions.initialWidth) {
                this.renderer.setStyle(this.resizeContainer.nativeElement, 'width', this.resizeOptions.initialWidth);
            }
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                    modifiers: [
                        interact.modifiers!.restrictSize({
                            min: { width: 15, height: 0 },
                            max: { width: this.resizeOptions.maxWidth ? this.resizeOptions.maxWidth : 2500, height: 2000 },
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
                    target.style.width = event.rect.width + 'px';
                });
        }
    }

    ngOnDestroy() {
        if (this.apollonEditor !== null) {
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

            if (this.apollonEditor !== null) {
                this.applyStateConfiguration();
            }
        }
        if (changes.centeredElementId) {
            if (this.centeredElementId) {
                this.scrollIntoView(this.centeredElementId);
            }
        }
    }

    /**
     * Initializes the Apollon editor after updating the Feedback accordingly. It also subscribes to change
     * events of Apollon an passes them on to parent components.
     */
    private initializeApollonEditor() {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        this.handleFeedback();

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            mode: ApollonMode.Assessment,
            readonly: this.readOnly,
            model: this.model,
            type: this.diagramType,
            enablePopups: this.enablePopups,
        });
        this.apollonEditor.subscribeToSelectionChange((selection: Selection) => {
            if (this.readOnly) {
                this.selectionChanged.emit(selection);
            }
        });
        if (!this.readOnly) {
            this.apollonEditor.subscribeToAssessmentChange((assessments: Assessment[]) => {
                this.feedbacks = this.generateFeedbackFromAssessment(assessments);
                this.calculateTotalScore();
                this.feedbackChanged.emit(this.feedbacks);
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
        for (const assessment of assessments) {
            const existingFeedback = this.elementFeedback.get(assessment.modelElementId);
            if (existingFeedback) {
                existingFeedback.credits = assessment.score;
                existingFeedback.text = assessment.feedback || null;
            } else {
                this.elementFeedback.set(assessment.modelElementId, Feedback.forModeling(assessment.score, assessment.feedback, assessment.modelElementId, assessment.elementType));
            }
        }
        return [...this.elementFeedback.values()];
    }

    /**
     * Handles (new) feedback by removing invalid feedback, updating the element-feedback mapping and updating
     * the assessments for Apollon accordingly. It also calculates the (new) total score of the assessment
     * which is then shown in the score display component.
     * This method is called before initializing Apollon and when the feedback or model is updated.
     */
    private handleFeedback(): void {
        this.feedbacks = this.removeInvalidFeedback(this.feedbacks);
        this.updateElementFeedbackMapping(this.feedbacks);
        this.updateApollonAssessments(this.feedbacks);
        this.calculateTotalScore();
    }

    /**
     * Removes feedback elements for which the corremosponding model element does not exist in the model anymore.
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
        //Hanya: we should not filter out feedback with no reference as we allow tutors to add unreferenced feedback
        //return feedbacks.filter((feedback) => availableIds.includes(feedback.referenceId!));
        return feedbacks;
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

        if (this.apollonEditor !== null) {
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
        if (!feedbacks) {
            return;
        }
        this.model.assessments = feedbacks.map<Assessment>((feedback) => ({
            modelElementId: feedback.referenceId!,
            elementType: feedback.referenceType! as UMLElementType | UMLRelationshipType,
            score: feedback.credits!,
            feedback: feedback.text || undefined,
        }));
        if (this.apollonEditor) {
            this.apollonEditor!.model = this.model;
        }
    }

    /**
     * Calculates the total score of the current assessment.
     * This function originally checked whether the total score is negative
     * or greater than the max. score, but we decided to remove the restriction
     * and instead set the score boundaries on the server.
     */
    private calculateTotalScore() {
        if (!this.feedbacks || this.feedbacks.length === 0) {
            this.totalScore = 0;
            return;
        }
        let totalScore = 0;
        for (const feedback of this.feedbacks) {
            totalScore += feedback.credits!;
        }
        this.totalScore = totalScore;
    }
}
