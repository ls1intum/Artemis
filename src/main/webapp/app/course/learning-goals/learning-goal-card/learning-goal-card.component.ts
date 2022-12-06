import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LearningGoalDetailModalComponent } from 'app/course/learning-goals/learning-goal-detail-modal/learning-goal-detail-modal.component';
import { LearningGoalCourseDetailModalComponent } from 'app/course/learning-goals/learning-goal-course-detail-modal/learning-goal-course-detail-modal.component';

@Component({
    selector: 'jhi-learning-goal-card',
    templateUrl: './learning-goal-card.component.html',
    styleUrls: ['./learning-goal-card.component.scss'],
})
export class LearningGoalCardComponent implements OnInit, OnDestroy {
    @Input()
    learningGoal: LearningGoal;
    @Input()
    progress?: number;
    @Input()
    isPrerequisite: boolean;
    @Input()
    displayOnly: boolean;

    public predicate = 'id';
    public reverse = false;
    public isProgressAvailable = false;

    public DetailModalComponent = LearningGoalDetailModalComponent;
    public CourseDetailModalComponent = LearningGoalCourseDetailModalComponent;

    constructor(private modalService: NgbModal, public lectureUnitService: LectureUnitService, public translateService: TranslateService) {}

    ngOnInit(): void {
        this.isProgressAvailable = !this.isPrerequisite;
    }

    ngOnDestroy(): void {
        if (this.modalService.hasOpenModals()) {
            this.modalService.dismissAll();
        }
    }
}
