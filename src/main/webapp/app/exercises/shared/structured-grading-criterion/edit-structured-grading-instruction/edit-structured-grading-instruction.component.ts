import { Component, OnInit, Input } from '@angular/core';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-edit-structured-grading-instruction',
    templateUrl: './edit-structured-grading-instruction.component.html',
    styleUrls: ['./edit-structured-grading-instruction.scss'],
})
export class EditStructuredGradingInstructionComponent implements OnInit {
    /** Ace Editor configuration constants **/
    questionEditorText = '';
    private criteria: GradingCriterion[];
    @Input()
    exercise: Exercise;
    entity: GradingCriterion[];
    isLoading: boolean;

    constructor() {}

    ngOnInit() {
        this.criteria = this.exercise.gradingCriteria;
        this.init();
    }

    private newEntity: GradingCriterion;

    /**
     * @function init
     * @desc Initializes local constants and prepares the Criterion entity
     */
    init(): void {
        if (this.criteria) {
            this.entity = this.criteria;
        } else {
            if (this.criteria === undefined) {
                this.entity = [];
            }
            this.newEntity = new GradingCriterion();
            this.newEntity.title = '';
            this.entity.push(this.newEntity);
            this.criteria = this.entity;
            this.exercise.gradingCriteria = this.criteria;
        }
    }
    /**
     * @function addGradingCriteria
     * @desc Add an empty grading criteria to the exercise with a dummy instruction
     */
    addGradingCriteria() {
        if (typeof this.criteria === 'undefined') {
            this.entity = [];
            this.criteria = this.entity;
        }
        this.newEntity = new GradingCriterion();
        this.newEntity.title = '';
        this.entity.push(this.newEntity);
        this.criteria = this.entity;
        this.exercise.gradingCriteria = this.criteria;
    }
    /**
     * @function deleteCriterion
     * @desc Remove criterion from the exercise
     * @param criterionToDelete {GradingCriterion} the criterion to remove
     */
    deleteCriterion(criterionToDelete: GradingCriterion): void {
        this.exercise.gradingCriteria = this.exercise.gradingCriteria.filter(criterion => criterion !== criterionToDelete);
        this.ngOnInit();
    }
}
