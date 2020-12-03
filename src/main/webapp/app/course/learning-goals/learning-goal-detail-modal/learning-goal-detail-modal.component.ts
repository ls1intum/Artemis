import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-learning-goal-detail-modal',
    templateUrl: './learning-goal-detail-modal.component.html',
})
export class LearningGoalDetailModalComponent implements OnInit {
    @Input()
    learningGoal: LearningGoal;
    public predicate = 'id';
    public reverse = false;

    constructor(public activeModal: NgbActiveModal, public lectureUnitService: LectureUnitService, public sortService: SortService) {}

    ngOnInit(): void {}

    sortRows() {
        if (this.learningGoal.lectureUnits) {
            this.sortService.sortByProperty(this.learningGoal.lectureUnits, this.predicate, this.reverse);
        }
    }
}
