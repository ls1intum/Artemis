import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { SortService } from 'app/shared/service/sort.service';
import { LearningGoalProgress } from 'app/course/learning-goals/learning-goal-progress-dtos.model';

@Component({
    selector: 'jhi-learning-goal-detail-modal',
    templateUrl: './learning-goal-detail-modal.component.html',
})
export class LearningGoalDetailModalComponent implements OnInit {
    @Input()
    learningGoal: LearningGoal;
    @Input()
    learningGoalProgress: LearningGoalProgress;
    public isProgressAvailable = false;

    public progressInPercent = 0;
    public connectedLectureUnitsPredicate = 'id';
    public connectedLectureUnitsReverse = false;

    public connectedLectureUnitsForCalculationPredicate = 'id';
    public connectedLectureUnitsForCalculationReverse = false;

    constructor(public activeModal: NgbActiveModal, public lectureUnitService: LectureUnitService, public sortService: SortService) {}

    ngOnInit(): void {
        if (this.learningGoalProgress && this.learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal > 0) {
            this.isProgressAvailable = true;
            this.progressInPercent = Math.round(
                (this.learningGoalProgress.pointsAchievedByStudentInLearningGoal / this.learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal) * 100,
            );
        }
    }

    sortConnectedLectureUnits() {
        if (this.learningGoal.lectureUnits) {
            this.sortService.sortByProperty(this.learningGoal.lectureUnits, this.connectedLectureUnitsPredicate, this.connectedLectureUnitsReverse);
        }
    }

    sortConnectedLectureUnitsForCalculation() {
        if (this.learningGoalProgress.progressInLectureUnits) {
            this.sortService.sortByProperty(
                this.learningGoalProgress.progressInLectureUnits,
                this.connectedLectureUnitsForCalculationPredicate,
                this.connectedLectureUnitsForCalculationReverse,
            );
        }
    }
}
