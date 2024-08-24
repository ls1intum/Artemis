import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { faArrowRight, faCheck, faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { LectureUpdateWizardCompetenciesComponent } from 'app/lecture/wizard-mode/lecture-wizard-competencies.component';
import { take } from 'rxjs/operators';

@Component({
    selector: 'jhi-lecture-update-wizard',
    templateUrl: './lecture-update-wizard.component.html',
    styleUrls: ['./lecture-update-wizard.component.scss'],
})
export class LectureUpdateWizardComponent implements OnInit {
    readonly faCheck = faCheck;
    readonly faHandShakeAngle = faHandshakeAngle;
    readonly faArrowRight = faArrowRight;

    @Input() toggleModeFunction: () => void;
    @Input() saveLectureFunction: () => void;
    @Input() validateDatesFunction: () => void;
    @Input() lecture: Lecture;
    @Input() isSaving: boolean;

    @ViewChild(LectureUpdateWizardUnitsComponent, { static: false }) unitsComponent: LectureUpdateWizardUnitsComponent;
    @ViewChild(LectureUpdateWizardCompetenciesComponent, { static: false }) competenciesComponent: LectureUpdateWizardCompetenciesComponent;

    currentStep: number;

    constructor(
        protected courseService: CourseManagementService,
        protected activatedRoute: ActivatedRoute,
        private router: Router,
    ) {}

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

    progressToNextStep() {
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

    isStepCompleted(step: number) {
        return this.currentStep > step;
    }

    isCurrentStep(step: number) {
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
