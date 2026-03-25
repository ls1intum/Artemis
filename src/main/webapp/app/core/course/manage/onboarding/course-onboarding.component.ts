import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from '../services/course-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { OnboardingGeneralSettingsComponent } from './pages/onboarding-general-settings.component';
import { OnboardingEnrollmentComponent } from './pages/onboarding-enrollment.component';
import { OnboardingCommunicationComponent } from './pages/onboarding-communication.component';
import { OnboardingAssessmentAiComponent } from './pages/onboarding-assessment-ai.component';
import { OnboardingExploreComponent } from './pages/onboarding-explore.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChartLine, faCheck, faChevronLeft, faChevronRight, faCog, faComments, faRocket, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-course-onboarding',
    templateUrl: './course-onboarding.component.html',
    styleUrls: ['./course-onboarding.component.scss'],
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        OnboardingGeneralSettingsComponent,
        OnboardingEnrollmentComponent,
        OnboardingCommunicationComponent,
        OnboardingAssessmentAiComponent,
        OnboardingExploreComponent,
    ],
})
export class CourseOnboardingComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private destroyRef = inject(DestroyRef);

    readonly activeStep = signal(0);
    readonly isSaving = signal(false);
    readonly course = signal<Course>(new Course());

    readonly stepKeys = ['generalSettings', 'enrollment', 'communication', 'assessment', 'explore'];
    readonly totalSteps = this.stepKeys.length;

    protected readonly stepIcons = [faCog, faUserPlus, faComments, faChartLine, faRocket];
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faCheck = faCheck;
    protected readonly faRocket = faRocket;

    readonly isLastStep = computed(() => this.activeStep() === this.totalSteps - 1);
    readonly isFirstStep = computed(() => this.activeStep() === 0);
    readonly canFinish = computed(() => this.activeStep() === this.totalSteps - 2);

    ngOnInit() {
        this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            const step = parseInt(params['step'], 10);
            if (!isNaN(step) && step >= 0 && step < this.totalSteps) {
                this.activeStep.set(step);
            }
        });
        this.route.data.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
            }
        });
    }

    nextStep() {
        if (this.activeStep() < this.totalSteps - 1) {
            if (!this.validateCurrentStep()) {
                return;
            }
            this.saveAndAdvance();
        }
    }

    previousStep() {
        if (this.activeStep() > 0) {
            this.saveAndNavigate(this.activeStep() - 1);
        }
    }

    goToStep(index: number) {
        if (index !== this.activeStep() && !this.isSaving()) {
            if (index > this.activeStep() && !this.validateCurrentStep()) {
                return;
            }
            this.saveAndNavigate(index);
        }
    }

    finishSetup() {
        if (!this.validateCurrentStep()) {
            return;
        }
        const current = this.course();
        current.onboardingDone = true;
        if (!current.id) {
            return;
        }
        this.isSaving.set(true);
        this.courseManagementService.update(current.id, current).subscribe({
            next: (response) => {
                this.isSaving.set(false);
                if (response.body) {
                    this.course.set(response.body);
                }
                const lastStep = this.totalSteps - 1;
                this.activeStep.set(lastStep);
                this.updateStepUrl(lastStep);
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                current.onboardingDone = false;
                onError(this.alertService, error);
            },
        });
    }

    goToCourse() {
        const current = this.course();
        if (!current.id) {
            return;
        }
        if (!current.onboardingDone) {
            current.onboardingDone = true;
            this.courseManagementService.update(current.id, current).subscribe({
                next: () => this.router.navigate(['course-management', current.id], { queryParams: { fromOnboarding: true } }),
                error: (error: HttpErrorResponse) => {
                    current.onboardingDone = false;
                    onError(this.alertService, error);
                },
            });
        } else {
            this.router.navigate(['course-management', current.id], { queryParams: { fromOnboarding: true } });
        }
    }

    onCourseUpdated(updatedCourse: Course) {
        this.course.set(updatedCourse);
    }

    validateCurrentStep(): boolean {
        const current = this.course();
        const step = this.activeStep();

        switch (step) {
            case 0: {
                // General Settings: startDate < endDate
                if (current.startDate && current.endDate && dayjs(current.startDate).isAfter(dayjs(current.endDate))) {
                    this.alertService.error('artemisApp.course.onboarding.validation.startDateBeforeEndDate');
                    return false;
                }
                break;
            }
            case 1: {
                if (!current.enrollmentEnabled) {
                    break;
                }
                if (current.enrollmentStartDate && current.enrollmentEndDate) {
                    if (!dayjs(current.enrollmentStartDate).isBefore(dayjs(current.enrollmentEndDate))) {
                        this.alertService.error('artemisApp.course.onboarding.validation.enrollmentStartDateBeforeEndDate');
                        return false;
                    }
                    if (!current.startDate || !current.endDate) {
                        this.alertService.error('artemisApp.course.onboarding.validation.enrollmentRequiresCourseDates');
                        return false;
                    }
                    if (dayjs(current.enrollmentEndDate).isAfter(dayjs(current.endDate))) {
                        this.alertService.error('artemisApp.course.onboarding.validation.enrollmentEndDateBeforeCourseEndDate');
                        return false;
                    }
                }
                if (current.unenrollmentEnabled && current.unenrollmentEndDate) {
                    if (!current.enrollmentStartDate || !current.enrollmentEndDate) {
                        this.alertService.error('artemisApp.course.onboarding.validation.unenrollmentRequiresEnrollmentDates');
                        return false;
                    }
                    if (!dayjs(current.enrollmentEndDate).isBefore(dayjs(current.unenrollmentEndDate))) {
                        this.alertService.error('artemisApp.course.onboarding.validation.unenrollmentAfterEnrollmentEnd');
                        return false;
                    }
                    if (current.endDate && dayjs(current.unenrollmentEndDate).isAfter(dayjs(current.endDate))) {
                        this.alertService.error('artemisApp.course.onboarding.validation.unenrollmentBeforeCourseEndDate');
                        return false;
                    }
                }
                break;
            }
            case 3: {
                // Assessment: accuracyOfScores 0-5, maxPoints > 0
                if (current.accuracyOfScores !== undefined && (current.accuracyOfScores < 0 || current.accuracyOfScores > 5)) {
                    this.alertService.error('artemisApp.course.onboarding.validation.accuracyOfScoresRange');
                    return false;
                }
                if (current.maxPoints !== undefined && current.maxPoints <= 0) {
                    this.alertService.error('artemisApp.course.onboarding.validation.maxPointsPositive');
                    return false;
                }
                break;
            }
        }
        return true;
    }

    private saveAndAdvance() {
        const current = this.course();
        if (!current.id) {
            const newStep = this.activeStep() + 1;
            this.activeStep.set(newStep);
            this.updateStepUrl(newStep);
            return;
        }
        this.isSaving.set(true);
        this.courseManagementService.update(current.id, current).subscribe({
            next: (response) => {
                this.isSaving.set(false);
                if (response.body) {
                    this.course.set(response.body);
                }
                const newStep = this.activeStep() + 1;
                this.activeStep.set(newStep);
                this.updateStepUrl(newStep);
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                onError(this.alertService, error);
            },
        });
    }

    private saveAndNavigate(targetStep: number) {
        const current = this.course();
        if (!current.id) {
            this.activeStep.set(targetStep);
            this.updateStepUrl(targetStep);
            return;
        }
        this.isSaving.set(true);
        this.courseManagementService.update(current.id, current).subscribe({
            next: (response) => {
                this.isSaving.set(false);
                if (response.body) {
                    this.course.set(response.body);
                }
                this.activeStep.set(targetStep);
                this.updateStepUrl(targetStep);
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                onError(this.alertService, error);
            },
        });
    }

    private updateStepUrl(step: number) {
        this.router.navigate([], { queryParams: { step }, queryParamsHandling: 'merge', replaceUrl: true });
    }
}
