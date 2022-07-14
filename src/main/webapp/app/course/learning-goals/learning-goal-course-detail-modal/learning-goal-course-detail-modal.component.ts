import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { SortService } from 'app/shared/service/sort.service';
import { CourseLearningGoalProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';
import { round } from 'app/shared/util/utils';
import { faSort } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-learning-goal-course-detail-modal',
    templateUrl: './learning-goal-course-detail-modal.component.html',
})
export class LearningGoalCourseDetailModalComponent implements OnInit {
    readonly round = round;
    @Input()
    learningGoal: LearningGoal;
    @Input()
    learningGoalCourseProgress: CourseLearningGoalProgress;
    @Input()
    isPrerequisite: Boolean;

    public lectureUnitIdToLectureUnitCourseProgress = new Map();

    public isProgressAvailable = false;
    public progressInPercent = 0;
    public connectedLectureUnitsPredicate = 'id';
    public connectedLectureUnitsReverse = false;

    // Icons
    faSort = faSort;

    constructor(public activeModal: NgbActiveModal, public lectureUnitService: LectureUnitService, public sortService: SortService) {}

    ngOnInit(): void {
        if (!this.isPrerequisite && this.learningGoalCourseProgress) {
            this.isProgressAvailable = true;

            if (this.learningGoalCourseProgress.progressInLectureUnits) {
                this.lectureUnitIdToLectureUnitCourseProgress = new Map(this.learningGoalCourseProgress.progressInLectureUnits.map((i) => [i.lectureUnitId, i]));
            }

            const progress =
                (this.learningGoalCourseProgress.averagePointsAchievedByStudentInLearningGoal / this.learningGoalCourseProgress.totalPointsAchievableByStudentsInLearningGoal) *
                    100 || 0;
            this.progressInPercent = round(progress, 1);
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
