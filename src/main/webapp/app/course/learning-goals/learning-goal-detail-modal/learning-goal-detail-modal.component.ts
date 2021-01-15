import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { SortService } from 'app/shared/service/sort.service';
import { IndividualLearningGoalProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';

@Component({
    selector: 'jhi-learning-goal-detail-modal',
    templateUrl: './learning-goal-detail-modal.component.html',
})
export class LearningGoalDetailModalComponent implements OnInit {
    @Input()
    learningGoal: LearningGoal;
    @Input()
    learningGoalProgress: IndividualLearningGoalProgress;

    public lectureUnitIdToLectureUnitProgress = new Map();

    public isProgressAvailable = false;
    public progressInPercent = 0;
    public connectedLectureUnitsPredicate = 'id';
    public connectedLectureUnitsReverse = false;

    constructor(public activeModal: NgbActiveModal, public lectureUnitService: LectureUnitService, public sortService: SortService) {}

    ngOnInit(): void {
        if (this.learningGoalProgress && this.learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal > 0) {
            this.isProgressAvailable = true;

            if (this.learningGoalProgress.progressInLectureUnits) {
                this.lectureUnitIdToLectureUnitProgress = new Map(this.learningGoalProgress.progressInLectureUnits.map((i) => [i.lectureUnitId, i]));
            }

            const progress = (this.learningGoalProgress.pointsAchievedByStudentInLearningGoal / this.learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal) * 100;
            this.progressInPercent = Math.round(progress * 10) / 10;
        }
    }

    getLectureUnitProgress(lectureUnitId: number) {
        return this.lectureUnitIdToLectureUnitProgress.get(lectureUnitId);
    }

    sortConnectedLectureUnits() {
        if (this.learningGoal.lectureUnits) {
            this.sortService.sortByProperty(this.learningGoal.lectureUnits, this.connectedLectureUnitsPredicate, this.connectedLectureUnitsReverse);
        }
    }
}
