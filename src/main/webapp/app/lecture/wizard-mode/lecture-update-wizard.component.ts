import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faCheck, faHandshakeAngle, faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { LectureUpdateWizardLearningGoalsComponent } from 'app/lecture/wizard-mode/lecture-wizard-learning-goals.component';
import { take } from 'rxjs/operators';

@Component({
    selector: 'jhi-lecture-update-wizard',
    templateUrl: './lecture-update-wizard.component.html',
    styleUrls: ['./lecture-update-wizard.component.scss'],
})
export class LectureUpdateWizardComponent implements OnInit {
    @Input() toggleModeFunction: () => void;
    @Input() saveLectureFunction: () => void;
    @Input() lecture: Lecture;
    @Input() isSaving: boolean;
    @Input() startDate: string;
    @Input() endDate: string;

    @ViewChild(LectureUpdateWizardUnitsComponent, { static: false }) unitsComponent: LectureUpdateWizardUnitsComponent;
    @ViewChild(LectureUpdateWizardLearningGoalsComponent, { static: false }) learningGoalsComponent: LectureUpdateWizardLearningGoalsComponent;

    currentStep: number;

    // Icons
    faCheck = faCheck;
    faHandShakeAngle = faHandshakeAngle;
    faArrowRight = faArrowRight;

    constructor(
        protected courseService: CourseManagementService,
        protected activatedRoute: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isSaving = false;

        this.activatedRoute.queryParams.pipe(take(1)).subscribe((params) => {
            if (params.step && !isNaN(+params.step)) {
                this.currentStep = +params.step;
            } else {
                this.currentStep = this.lecture.id ? 5 : this.lecture.startDate !== undefined || this.lecture.endDate !== undefined ? 2 : 1;
            }

            this.router.navigate([], {
                relativeTo: this.activatedRoute,
                queryParamsHandling: '',
                replaceUrl: true,
            });
        });
    }

    /**
     * Progress to the next step of the wizard mode
     */
    next() {
        if (this.currentStep === 2 || this.currentStep === 5) {
            this.saveLectureFunction();
            return;
        }

        this.currentStep++;
    }

    /**
     * Called when the lecture has been successfully created in the parent component to advance in the wizard
     */
    onLectureCreationSucceeded() {
        this.currentStep++;
    }

    /**
     * Checks if the given step has already been completed
     */
    isCompleted(step: number) {
        return this.currentStep > step;
    }

    /**
     * Checks if the given step is the current one
     */
    isCurrent(step: number) {
        return this.currentStep === step;
    }

    getNextIcon() {
        return this.currentStep < 5 ? faArrowRight : faCheck;
    }

    getNextText() {
        return this.currentStep < 5 ? 'artemisApp.lecture.home.nextStepLabel' : 'entity.action.finish';
    }

    toggleWizardMode() {
        if (this.currentStep <= 2) {
            this.toggleModeFunction();
        } else {
            this.router.navigate(['course-management', this.lecture.course!.id, 'lectures', this.lecture.id]);
        }
    }
}
