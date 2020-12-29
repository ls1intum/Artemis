import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { SortService } from 'app/shared/service/sort.service';
import { CourseLearningGoalProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';

@Component({
    selector: 'jhi-learning-goal-course-detail-modal',
    templateUrl: './learning-goal-course-detail-modal.component.html',
})
export class LearningGoalCourseDetailModalComponent implements OnInit {
    @Input()
    learningGoal: LearningGoal;
    @Input()
    learningGoalCourseProgress: CourseLearningGoalProgress;

    public lectureUnitIdToLectureUnitCourseProgress = new Map();

    public isProgressAvailable = false;
    public progressInPercent = 0;
    public connectedLectureUnitsPredicate = 'id';
    public connectedLectureUnitsReverse = false;

    constructor(public activeModal: NgbActiveModal, public lectureUnitService: LectureUnitService, public sortService: SortService) {}

    ngOnInit(): void {
        if (this.learningGoalCourseProgress && this.learningGoalCourseProgress.totalPointsAchievableByStudentsInLearningGoal > 0) {
            this.isProgressAvailable = true;

            if (this.learningGoalCourseProgress.progressInLectureUnits) {
                this.lectureUnitIdToLectureUnitCourseProgress = new Map(this.learningGoalCourseProgress.progressInLectureUnits.map((i) => [i.lectureUnitId, i]));
            }

            const progress =
                (this.learningGoalCourseProgress.averagePointsAchievedByStudentInLearningGoal / this.learningGoalCourseProgress.totalPointsAchievableByStudentsInLearningGoal) *
                100;
            this.progressInPercent = Math.round(progress * 10) / 10;
        }
    }

    getLectureUnitCourseProgress(lectureUnitId: number) {
        return this.lectureUnitIdToLectureUnitCourseProgress.get(lectureUnitId);
    }

    sortConnectedLectureUnits() {
        if (this.learningGoal.lectureUnits) {
            this.sortService.sortByProperty(this.learningGoal.lectureUnits, this.connectedLectureUnitsPredicate, this.connectedLectureUnitsReverse);
        }
    }
}
