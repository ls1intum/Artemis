import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, Output, Renderer2, SimpleChanges, ViewChild } from '@angular/core';
import { ApollonEditor, ApollonMode, Assessment, DiagramType, Selection, UMLModel } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import * as interact from 'interactjs';
import { Feedback } from 'app/entities/feedback';
import { User } from 'app/core';
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

    @ViewChild('editorContainer') editorContainer: ElementRef;

    @ViewChild('resizeContainer') resizeContainer: ElementRef;
    @Input() model: UMLModel;
    @Input() highlightedElementIds: Set<string>;
    @Input() highlightColor = 'rgba(219, 53, 69,0.6)';
    @Input() centeredElementId: string;
    @Input() feedbacks: Feedback[] = [];
    @Input() diagramType: DiagramType;
    @Input() maxScore: number;
    @Input() title: string;
    @Input() resizeOptions: { initialWidth: string; maxWidth?: number };
    @Input() readOnly = false;
    @Input() enablePopups = true;
    @Input() displayPoints = true;
    @Output() feedbackChanged = new EventEmitter<Feedback[]>();
    @Output() selectionChanged = new EventEmitter<Selection>();

    constructor(private jhiAlertService: JhiAlertService, private renderer: Renderer2) {}

    ngAfterViewInit(): void {
        if (this.model) {
            this.initializeApollonEditor();
        } else {
            this.jhiAlertService.error('modelingAssessment.noModel');
        }
        this.applyStateConfiguration();
        if (this.resizeOptions) {
            if (this.resizeOptions.initialWidth) {
                this.renderer.setStyle(this.resizeContainer.nativeElement, 'width', this.resizeOptions.initialWidth);
            }
            interact('.resizable')
                .resizable({
                    edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                    restrictSize: {
                        min: { width: 15 },
                        max: { width: this.resizeOptions.maxWidth ? this.resizeOptions.maxWidth : 2500 },
                    },
                    inertia: true,
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
        if (changes.feedbacks && changes.feedbacks.currentValue && this.model) {
            this.feedbacks = changes.feedbacks.currentValue;
            this.updateElementFeedbackMapping(this.feedbacks, true);
            this.updateApollonAssessments(this.feedbacks);
            this.calculateTotalScore();
            this.applyStateConfiguration();
        }
        if (changes.highlightedElementIds || changes.highlightColor) {
            if (changes.highlightColor) {
                this.highlightColor = changes.highlightColor.currentValue;
            }
            if (changes.highlightedElementIds) {
                this.highlightedElementIds = changes.highlightedElementIds.currentValue;
            }
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

    private initializeApollonEditor() {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
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
        if (this.highlightedElementIds) {
            this.updateHighlightedElements(this.highlightedElementIds);
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

    private updateHighlightedElements(newElementIDs: Set<string>) {
        if (!newElementIDs) {
            newElementIDs = new Set<string>();
        }
        if (this.apollonEditor !== null) {
            const model: UMLModel = this.apollonEditor.model;
            for (const element of model.elements) {
                if (newElementIDs.has(element.id)) {
                    element.highlight = this.highlightColor;
                } else {
                    element.highlight = undefined;
                }
            }
            for (const relationship of model.relationships) {
                if (newElementIDs.has(relationship.id)) {
                    relationship.highlight = this.highlightColor;
                } else {
                    relationship.highlight = undefined;
                }
            }
            this.apollonEditor.model = model;
        }
    }

    private scrollIntoView(elementId: string) {
        const element = this.editorContainer.nativeElement as HTMLElement;
        const matchingElement = $(element)
            .find(`#${elementId}`)
            .get(0);
        if (matchingElement) {
            matchingElement.scrollIntoView({ block: 'center', inline: 'center' });
        }
    }

    private updateApollonAssessments(feedbacks: Feedback[]) {
        this.model.assessments = feedbacks.map(feedback => {
            return {
                modelElementId: feedback.referenceId,
                elementType: feedback.referenceType,
                score: feedback.credits,
                feedback: feedback.text,
            };
        });
        if (this.apollonEditor) {
            this.apollonEditor.model = this.model;
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
        }
        let totalScore = 0;
        for (const feedback of this.feedbacks) {
            totalScore += feedback.credits;
        }
        this.totalScore = totalScore;
    }
}
