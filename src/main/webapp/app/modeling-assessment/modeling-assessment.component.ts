import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, Renderer2, ViewChild } from '@angular/core';
import { ApollonEditor, ApollonMode, DiagramType, UMLElement, UMLModel, UMLRelationship } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import * as interact from 'interactjs';
import { Feedback } from 'app/entities/feedback';
import { User } from 'app/core';

@Component({
    selector: 'jhi-modeling-assessment',
    templateUrl: './modeling-assessment.component.html',
    styleUrls: ['./modeling-assessment.component.scss'],
})
export class ModelingAssessmentComponent implements OnInit, AfterViewInit, OnDestroy {
    apollonEditor: ApollonEditor | null = null;
    elementFeedback: Map<string, Feedback>; // map element.id --> Feedback
    feedbacks: Feedback[] = [];
    totalScore = 0;

    @ViewChild('editorContainer') editorContainer: ElementRef;
    @ViewChild('resizeContainer') resizeContainer: ElementRef;
    @Input() model: UMLModel;
    @Input() initialFeedback: Feedback[];
    @Input() diagramType: DiagramType;
    @Input() maxScore: number;
    @Input() assessor: User;
    @Input() resizeOptions: { initialWidth: string; maxWidth?: number };
    @Input() readOnly = false;
    @Output() feedbackChanged = new EventEmitter<Feedback[]>();

    constructor(private jhiAlertService: JhiAlertService, private renderer: Renderer2) {}

    ngOnInit() {}

    ngAfterViewInit(): void {
        if (this.model) {
            this.updateElementFeedbackMapping(this.feedbacks, true);
            this.initializeApollonEditor();
        } else {
            this.jhiAlertService.error('arTeMiSApp.apollonDiagram.submission.noModel');
        }
        if (this.resizeOptions) {
            if (this.resizeOptions.initialWidth) {
                this.renderer.setStyle(this.resizeContainer.nativeElement, 'width', this.resizeOptions.initialWidth);
            }
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                    restrictSize: {
                        min: { width: 15 },
                        max: { width: this.resizeOptions.maxWidth | 2500 },
                    },
                    inertia: true,
                })
                .on('resizemove', event => {
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

    /**
     * Initializes the Apollon editor with the Feedback List in Assessment mode.
     * The Feedback elements are converted to Assessment objects needed by Apollon before they are added to
     * the initial model which is then passed to Apollon.
     */
    private initializeApollonEditor() {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
        if (!this.feedbacks || this.feedbacks.length === 0) {
            this.generateInitialFeedback();
        }
        this.model.assessments = this.feedbacks.map(feedback => {
            return {
                modelElementId: feedback.referenceId,
                elementType: feedback.referenceType,
                score: feedback.credits,
                feedback: feedback.text,
            };
        });

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            mode: ApollonMode.Assessment,
            readonly: this.readOnly,
            model: this.model,
            type: this.diagramType,
        });

        if (!this.readOnly) {
            this.apollonEditor.subscribeToSelectionChange(selection => {
                this.feedbacks = this.generateFeedbackFromAssessment();
                this.calculateTotalScore();
                this.feedbackChanged.emit(this.feedbacks);
            });
        }
    }

    /**
     * Gets the assessments from Apollon and creates/updates the corresponding Feedback entries in the
     * element feedback mapping.
     * Returns an array containing all feedback entries from the mapping.
     */
    private generateFeedbackFromAssessment(): Feedback[] {
        for (const assessment of this.apollonEditor.model.assessments) {
            const existingFeedback = this.elementFeedback.get(assessment.modelElementId);
            if (existingFeedback) {
                existingFeedback.credits = assessment.score;
                existingFeedback.text = assessment.feedback;
            } else {
                this.elementFeedback.set(assessment.modelElementId, new Feedback(assessment.modelElementId, assessment.elementType, assessment.score, assessment.feedback));
            }
        }
        return [...this.elementFeedback.values()];
    }

    /**
     * Updates the mapping of elementIds to Feedback elements. This should be called after getting the
     * (updated) Feedback list from the server.
     *
     * @param feedbacks new Feedback elements to insert
     * @param initialize initialize a new map, if this flag is true
     */
    private updateElementFeedbackMapping(feedbacks: Feedback[], initialize?: boolean) {
        if (initialize) {
            this.elementFeedback = new Map();
        }
        if (!feedbacks) {
            return;
        }
        for (const feedback of feedbacks) {
            this.elementFeedback.set(feedback.referenceId, feedback);
        }
    }

    private generateInitialFeedback() {
        this.model.elements.forEach((element: UMLElement) => {
            this.feedbacks.push(new Feedback(element.id, element.type, 0, undefined));
        });
        this.model.relationships.forEach((relationship: UMLRelationship) => {
            this.feedbacks.push(new Feedback(relationship.id, relationship.type, 0, undefined));
        });
    }

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     * This function originally checked whether the total score is negative
     * or greater than the max. score, but we decided to remove the restriction
     * and instead set the score boundaries on the server.
     */
    private calculateTotalScore() {
        if (!this.feedbacks || this.feedbacks.length === 0) {
            this.totalScore = 0;
        }
        let totalScore = 0;
        for (const feedback of this.feedbacks) {
            totalScore += feedback.credits;
        }
        this.totalScore = totalScore;
    }
}
