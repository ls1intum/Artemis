import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoal, LearningGoalProgress, LearningGoalTaxonomy, getIcon, getIconTooltip } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LearningGoalDetailModalComponent } from 'app/course/learning-goals/learning-goal-detail-modal/learning-goal-detail-modal.component';
import { LearningGoalCourseDetailModalComponent } from 'app/course/learning-goals/learning-goal-course-detail-modal/learning-goal-course-detail-modal.component';

@Component({
    selector: 'jhi-learning-goal-card',
    templateUrl: './learning-goal-card.component.html',
    styleUrls: ['../../../overview/course-exercises/course-exercise-row.scss'],
})
export class LearningGoalCardComponent implements OnInit, OnDestroy {
    @Input()
    courseId?: number;
    @Input()
    learningGoal: LearningGoal;
    @Input()
    isPrerequisite: boolean;
    @Input()
    displayOnly: boolean;

    public predicate = 'id';
    public reverse = false;
    public isProgressAvailable = false;

    public DetailModalComponent = LearningGoalDetailModalComponent;
    public CourseDetailModalComponent = LearningGoalCourseDetailModalComponent;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    constructor(private modalService: NgbModal, public lectureUnitService: LectureUnitService, public translateService: TranslateService) {}

    ngOnInit(): void {
        this.isProgressAvailable = !this.isPrerequisite;
    }

    ngOnDestroy(): void {
        if (this.modalService.hasOpenModals()) {
            this.modalService.dismissAll();
        }
    }

    getUserProgress(): LearningGoalProgress {
        if (this.learningGoal.userProgress?.length) {
            return this.learningGoal.userProgress.first()!;
        }
        return { progress: 0, confidence: 0 } as LearningGoalProgress;
    }

    get progress(): number {
        // The percentage of completed lecture units and participated exercises
        return this.getUserProgress().progress ?? 0;
    }

    get confidence(): number {
        // Confidence level (average score in exercises) in proportion to the threshold value (max. 100 %)
        // Example: If the student’s latest confidence level equals 60 % and the mastery threshold is set to 80 %, the ring would be 75 % full.
        return Math.min(Math.round(((this.getUserProgress().confidence ?? 0) / (this.learningGoal.masteryThreshold ?? 100)) * 100), 100);
    }

    get mastery(): number {
        // Advancement towards mastery as a weighted function of progress and confidence
        const weight = 2 / 3;
        return Math.round((1 - weight) * this.progress + weight * this.confidence);
    }
}
