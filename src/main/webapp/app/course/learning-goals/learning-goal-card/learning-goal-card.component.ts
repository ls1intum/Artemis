import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoal, LearningGoalProgress, LearningGoalTaxonomy } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LearningGoalDetailModalComponent } from 'app/course/learning-goals/learning-goal-detail-modal/learning-goal-detail-modal.component';
import { LearningGoalCourseDetailModalComponent } from 'app/course/learning-goals/learning-goal-course-detail-modal/learning-goal-course-detail-modal.component';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faBrain, faComments, faCubesStacked, faMagnifyingGlass, faPenFancy, faPlusMinus, faQuestion } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-learning-goal-card',
    templateUrl: './learning-goal-card.component.html',
    styleUrls: ['../../../overview/course-exercises/course-exercise-row.scss'],
})
export class LearningGoalCardComponent implements OnInit, OnDestroy {
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
        // Confidence level (average score in exercises) in proportion to the threshold value
        // Example: If the student’s latest confidence level equals 60 % and the mastery threshold is set to 80 %, the ring would be 75 % full.
        return ((this.getUserProgress().confidence ?? 0) / (this.learningGoal.masteryThreshold ?? 100)) * 100;
    }

    get mastery(): number {
        // Advancement towards mastery as a weighted function of progress and confidence
        const weight = 2 / 3;
        return (1 - weight) * this.progress + weight * this.confidence;
    }

    getIcon(learningGoalTaxonomy?: LearningGoalTaxonomy): IconProp {
        if (!learningGoalTaxonomy) {
            return faQuestion as IconProp;
        }

        const icons = {
            [LearningGoalTaxonomy.REMEMBER]: faBrain,
            [LearningGoalTaxonomy.UNDERSTAND]: faComments,
            [LearningGoalTaxonomy.APPLY]: faPenFancy,
            [LearningGoalTaxonomy.ANALYZE]: faMagnifyingGlass,
            [LearningGoalTaxonomy.EVALUATE]: faPlusMinus,
            [LearningGoalTaxonomy.CREATE]: faCubesStacked,
        };

        return icons[learningGoalTaxonomy] as IconProp;
    }

    getIconTooltip(learningGoalTaxonomy?: LearningGoalTaxonomy): string {
        if (!learningGoalTaxonomy) {
            return '';
        }

        const tooltips = {
            [LearningGoalTaxonomy.REMEMBER]: 'artemisApp.learningGoal.taxonomies.remember',
            [LearningGoalTaxonomy.UNDERSTAND]: 'artemisApp.learningGoal.taxonomies.understand',
            [LearningGoalTaxonomy.APPLY]: 'artemisApp.learningGoal.taxonomies.apply',
            [LearningGoalTaxonomy.ANALYZE]: 'artemisApp.learningGoal.taxonomies.analyze',
            [LearningGoalTaxonomy.EVALUATE]: 'artemisApp.learningGoal.taxonomies.evaluate',
            [LearningGoalTaxonomy.CREATE]: 'artemisApp.learningGoal.taxonomies.create',
        };

        return tooltips[learningGoalTaxonomy];
    }
}
