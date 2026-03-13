import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
    readonly canFinish = computed(() => this.activeStep() >= this.totalSteps - 2);

    ngOnInit() {
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
            }
        });
    }

    nextStep() {
        if (this.activeStep() < this.totalSteps - 1) {
            this.saveAndAdvance();
        }
    }

    previousStep() {
        if (this.activeStep() > 0) {
            this.activeStep.update((s) => s - 1);
        }
    }

    finishSetup() {
        const current = { ...this.course(), onboardingDone: true };
        this.isSaving.set(true);
        this.courseManagementService.update(current.id!, current).subscribe({
            next: (response) => {
                this.isSaving.set(false);
                if (response.body) {
                    this.course.set(response.body);
                }
                this.router.navigate(['course-management', current.id]);
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                onError(this.alertService, error);
            },
        });
    }

    onCourseUpdated(updatedCourse: Course) {
        this.course.set(updatedCourse);
    }

    private saveAndAdvance() {
        const current = this.course();
        if (!current.id) {
            this.activeStep.update((s) => s + 1);
            return;
        }
        this.isSaving.set(true);
        this.courseManagementService.update(current.id, current).subscribe({
            next: (response) => {
                this.isSaving.set(false);
                if (response.body) {
                    this.course.set(response.body);
                }
                this.activeStep.update((s) => s + 1);
            },
            error: (error: HttpErrorResponse) => {
                this.isSaving.set(false);
                onError(this.alertService, error);
            },
        });
    }
}
