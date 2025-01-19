import { Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { EditableSliderComponent } from 'app/shared/editable-slider/editable-slider.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LearnerProfileApiService } from 'app/atlas/service/learner-profile-api.service';
import { CourseLearnerProfileDTO } from 'app/entities/learner-profile.model';
import { EditStateTransition } from 'app/shared/editable-slider/edit-process.component';
import { AlertService, AlertType } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    standalone: true,
    imports: [EditableSliderComponent],
})
export class CourseLearnerProfileComponent implements OnInit {
    private readonly learnerProfileAPIService = inject(LearnerProfileApiService);

    courses: Course[];
    courseLearnerProfiles: Record<number, CourseLearnerProfileDTO>;
    activeCourse: number;

    disabled = true;
    readonly aimForGradeOrBonus = signal<number>(0);
    readonly timeInvestment = signal<number>(0);
    readonly repetitionIntensity = signal<number>(0);

    aimForGradeOrBonusState = signal<EditStateTransition>(EditStateTransition.Abort);
    timeInvestmentState = signal<EditStateTransition>(EditStateTransition.Abort);
    repetitionIntensityState = signal<EditStateTransition>(EditStateTransition.Abort);

    constructor(
        private courseService: CourseManagementService,
        private alertService: AlertService,
    ) {}

    async ngOnInit() {
        await this.loadProfiles();
        this.loadCourses();
    }

    courseChanged(event: Event) {
        const val: string = (<HTMLSelectElement>event.target).value;
        this.aimForGradeOrBonusState.set(EditStateTransition.Abort);

        if (val != '-1') {
            this.activeCourse = Number(val);
            this.disabled = false;
            const clp = this.courseLearnerProfiles[this.activeCourse];
            this.aimForGradeOrBonus.set(clp.aimForGradeOrBonus);
            this.timeInvestment.set(clp.timeInvestment);
            this.timeInvestment.set(clp.timeInvestment);
            this.repetitionIntensity.set(clp.repetitionIntensity);
        } else {
            this.disabled = true;
        }
    }

    onCourseLearnerProfileUpdateError(error: HttpErrorResponse, stateSignal: WritableSignal<EditStateTransition>) {
        stateSignal.set(EditStateTransition.Abort);

        console.log('Error');
        console.log(error);

        const errorMessage = error.error ? error.error.title : error.headers?.get('x-artemisapp-alert');
        console.log(errorMessage);
        if (errorMessage) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: errorMessage,
                disableTranslation: true,
            });
        }
    }

    updateAimForGradeOrBonus(value: number) {
        const clp = this.courseLearnerProfiles[this.activeCourse];
        clp.aimForGradeOrBonus = value;
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(clp).then(
            (courseLearnerProfile) => {
                this.courseLearnerProfiles[this.activeCourse] = courseLearnerProfile;
                this.aimForGradeOrBonusState.set(EditStateTransition.Saved);
            },
            (res: HttpErrorResponse) => this.onCourseLearnerProfileUpdateError(res, this.aimForGradeOrBonusState),
        );
    }

    updateTimeInvestment(value: number) {
        const clp = this.courseLearnerProfiles[this.activeCourse];
        clp.timeInvestment = value;
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(clp).then(
            (courseLearnerProfile) => {
                this.courseLearnerProfiles[this.activeCourse] = courseLearnerProfile;
                this.timeInvestmentState.set(EditStateTransition.Saved);
            },
            (res: HttpErrorResponse) => this.onCourseLearnerProfileUpdateError(res, this.timeInvestmentState),
        );
    }

    updateRepetitionIntensity(value: number) {
        const clp = this.courseLearnerProfiles[this.activeCourse];
        clp.repetitionIntensity = value;
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(clp).then(
            (courseLearnerProfile) => {
                this.courseLearnerProfiles[this.activeCourse] = courseLearnerProfile;
                this.repetitionIntensityState.set(EditStateTransition.Saved);
            },
            (res: HttpErrorResponse) => this.onCourseLearnerProfileUpdateError(res, this.repetitionIntensityState),
        );
    }

    async loadProfiles() {
        this.courseLearnerProfiles = await this.learnerProfileAPIService.getCourseLearnerProfilesForCurrentUser();
    }

    loadCourses() {
        this.courseService.findAllForDropdown().subscribe({
            next: (res: HttpResponse<Course[]>) => {
                if (!res.body || res.body.length === 0) {
                    return;
                }
                this.courses = res.body.filter((course) => {
                    if (course.id) {
                        return Object.keys(this.courseLearnerProfiles).includes(course.id.toString());
                    }
                    return false;
                });
            },
        });
    }
}
