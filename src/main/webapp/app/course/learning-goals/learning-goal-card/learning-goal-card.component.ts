import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LearningGoalDetailModalComponent } from 'app/course/learning-goals/learning-goal-detail-modal/learning-goal-detail-modal.component';
import { IndividualLearningGoalProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { CourseLearningGoalProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';
import { LearningGoalCourseDetailModalComponent } from 'app/course/learning-goals/learning-goal-course-detail-modal/learning-goal-course-detail-modal.component';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-learning-goal-card',
    templateUrl: './learning-goal-card.component.html',
    styleUrls: ['./learning-goal-card.component.scss'],
})
export class LearningGoalCardComponent implements OnInit, OnDestroy {
    @Input()
    learningGoal: LearningGoal;
    @Input()
    learningGoalProgress: IndividualLearningGoalProgress | CourseLearningGoalProgress | undefined;
    @Input()
    isPrerequisite: Boolean;
    @Input()
    displayOnly: Boolean;

    public predicate = 'id';
    public reverse = false;
    public progressText = '';
    public progressInPercent = 0;
    public isProgressAvailable = false;

    public DetailModalComponent = LearningGoalDetailModalComponent;
    public CourseDetailModalComponent = LearningGoalCourseDetailModalComponent;

    constructor(private modalService: NgbModal, public lectureUnitService: LectureUnitService, public translateService: TranslateService) {}

    ngOnInit(): void {
        if (this.isPrerequisite || !this.learningGoalProgress) {
            this.isProgressAvailable = false;
        } else {
            this.isProgressAvailable = true;
            this.progressText = this.translateService.instant('artemisApp.learningGoal.learningGoalCard.achieved');
            let pointsAchieved;
            if (this.isIndividualProgress(this.learningGoalProgress)) {
                pointsAchieved = this.learningGoalProgress.pointsAchievedByStudentInLearningGoal;
            } else {
                pointsAchieved = this.learningGoalProgress.averagePointsAchievedByStudentInLearningGoal;
            }

            const progress = (pointsAchieved / this.learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal) * 100 || 0;
            this.progressInPercent = round(progress, 1);
        }
    }

    isIndividualProgress(progress: IndividualLearningGoalProgress | CourseLearningGoalProgress): progress is IndividualLearningGoalProgress {
        return (progress as IndividualLearningGoalProgress).studentId !== undefined;
    }

    isCourseProgress(progress: IndividualLearningGoalProgress | CourseLearningGoalProgress): progress is CourseLearningGoalProgress {
        return (progress as CourseLearningGoalProgress).courseId !== undefined;
    }

    ngOnDestroy(): void {
        if (this.modalService.hasOpenModals()) {
            this.modalService.dismissAll();
        }
    }

    /**
     * Opens the modal with details (e.g., progress report) about the clicked learning goal
     */
    openLearningGoalDetailsModal() {
        // For prerequisites do not open the modal for now
        // TODO: Later we will display connected lecture units also for prerequisites
        if (this.isPrerequisite) {
            return;
        }

        if (this.learningGoalProgress && this.isCourseProgress(this.learningGoalProgress)) {
            const modalRef = this.modalService.open(this.CourseDetailModalComponent, {
                size: 'lg',
            });
            if (modalRef) {
                modalRef.componentInstance.learningGoal = this.learningGoal;
                modalRef.componentInstance.learningGoalCourseProgress = this.learningGoalProgress;
                modalRef.componentInstance.isPrerequisite = this.isPrerequisite;
            }
        } else {
            const modalRef = this.modalService.open(this.DetailModalComponent, {
                size: 'lg',
            });
            if (modalRef) {
                modalRef.componentInstance.learningGoal = this.learningGoal;
                modalRef.componentInstance.learningGoalProgress = this.learningGoalProgress;
                modalRef.componentInstance.isPrerequisite = this.isPrerequisite;
            }
        }
    }
}
