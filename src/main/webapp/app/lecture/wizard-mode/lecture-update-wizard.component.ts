import { Component, Input, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Lecture } from 'app/entities/lecture.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faArrowRight, faCheck, faHandshakeAngle } from '@fortawesome/free-solid-svg-icons';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { take } from 'rxjs/operators';

@Component({
    selector: 'jhi-lecture-update-wizard',
    templateUrl: './lecture-update-wizard.component.html',
    styleUrls: ['./lecture-update-wizard.component.scss'],
})
export class LectureUpdateWizardComponent implements OnInit {
    protected courseService = inject(CourseManagementService);
    protected activatedRoute = inject(ActivatedRoute);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private router = inject(Router);

    @Input() toggleModeFunction: () => void;
    @Input() saveLectureFunction: () => void;
    @Input() validateDatesFunction: () => void;
    @Input() lecture: Lecture;
    @Input() isSaving: boolean;

    @ViewChild(LectureUpdateWizardUnitsComponent, { static: false }) unitsComponent: LectureUpdateWizardUnitsComponent;

    readonly LECTURE_UPDATE_WIZARD_TITLE_STEP = 1;
    readonly LECTURE_UPDATE_WIZARD_PERIOD_STEP = 2;
    readonly LECTURE_UPDATE_WIZARD_ATTACHMENT_STEP = 3;
    readonly LECTURE_UPDATE_WIZARD_UNIT_STEP = 4;

    currentStep: number;

    // Icons
    faCheck = faCheck;
    faHandShakeAngle = faHandshakeAngle;
    faArrowRight = faArrowRight;

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isSaving = false;

        this.activatedRoute.queryParams.pipe(take(1)).subscribe((params) => {
            if (params.step && !isNaN(+params.step)) {
                this.currentStep = +params.step;
            } else {
                if (this.lecture.id) {
                    this.currentStep = this.LECTURE_UPDATE_WIZARD_UNIT_STEP;
                } else if (this.lecture.startDate === undefined && this.lecture.endDate === undefined) {
                    this.currentStep = this.LECTURE_UPDATE_WIZARD_TITLE_STEP;
                } else if (!this.lecture.id) {
                    this.currentStep = this.LECTURE_UPDATE_WIZARD_PERIOD_STEP;
                }
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
        if (this.currentStep === this.LECTURE_UPDATE_WIZARD_PERIOD_STEP || this.currentStep === this.LECTURE_UPDATE_WIZARD_UNIT_STEP) {
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

    getNextIcon() {
        return this.currentStep < this.LECTURE_UPDATE_WIZARD_UNIT_STEP ? faArrowRight : faCheck;
    }

    getNextText() {
        return this.currentStep < this.LECTURE_UPDATE_WIZARD_UNIT_STEP ? 'artemisApp.lecture.home.nextStepLabel' : 'entity.action.finish';
    }

    toggleWizardMode() {
        if (this.currentStep <= this.LECTURE_UPDATE_WIZARD_PERIOD_STEP) {
            this.toggleModeFunction();
        } else {
            this.router.navigate(['course-management', this.lecture.course!.id, 'lectures', this.lecture.id]);
        }
    }
}
