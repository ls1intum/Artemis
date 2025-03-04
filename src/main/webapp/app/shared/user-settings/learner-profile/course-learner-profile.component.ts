import { Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { EditableSliderComponent } from 'app/shared/editable-slider/editable-slider.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { CourseLearnerProfileDTO } from 'app/entities/learner-profile.model';
import { EditStateTransition } from 'app/shared/editable-slider/edit-process.component';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    styleUrls: ['./course-learner-profile.component.scss'],
    imports: [EditableSliderComponent, TranslateDirective],
})
export class CourseLearnerProfileComponent implements OnInit {
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private learnerProfileAPIService = inject(LearnerProfileApiService);
    protected translateService = inject(TranslateService);

    courses: Course[];
    courseLearnerProfiles: Record<number, CourseLearnerProfileDTO>;
    activeCourse: number;

    disabled = true;
    aimForGradeOrBonus = signal<number>(0);
    timeInvestment = signal<number>(0);
    repetitionIntensity = signal<number>(0);

    aimForGradeOrBonusState = signal<EditStateTransition>(EditStateTransition.Abort);
    timeInvestmentState = signal<EditStateTransition>(EditStateTransition.Abort);
    repetitionIntensityState = signal<EditStateTransition>(EditStateTransition.Abort);

    async ngOnInit() {
        await this.loadProfiles();
        this.loadCourses();
    }

    courseChanged(event: Event) {
        const courseId: string = (<HTMLSelectElement>event.target).value;
        this.aimForGradeOrBonusState.set(EditStateTransition.Abort);
        this.timeInvestmentState.set(EditStateTransition.Abort);
        this.repetitionIntensity.set(EditStateTransition.Abort);

        // courseId of -1 represents no course selected
        if (courseId !== '-1') {
            this.activeCourse = Number(courseId);
            this.disabled = false;
            const courseLearnerProfile = this.courseLearnerProfiles[this.activeCourse];

            // Update displayed values to new course
            this.aimForGradeOrBonus.set(courseLearnerProfile.aimForGradeOrBonus);
            this.timeInvestment.set(courseLearnerProfile.timeInvestment);
            this.repetitionIntensity.set(courseLearnerProfile.repetitionIntensity);
        } else {
            this.disabled = true;
        }
    }

    onCourseLearnerProfileUpdateError(error: HttpErrorResponse, stateSignal: WritableSignal<EditStateTransition>) {
        stateSignal.set(EditStateTransition.Abort);

        const errorMessage = error.error ? error.error.title : error.headers?.get('x-artemisapp-alert');
        if (errorMessage) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: errorMessage,
                disableTranslation: true,
            });
        }
    }

    updateAimForGradeOrBonus(aimForGradeOrBonus: number) {
        //return if value is changed without user trying to save
        if (!this.courseLearnerProfiles || this.aimForGradeOrBonusState() != EditStateTransition.TrySave) {
            return;
        }
        const courseLearnerProfile = this.courseLearnerProfiles[this.activeCourse];
        courseLearnerProfile.aimForGradeOrBonus = aimForGradeOrBonus;

        // Try to update profile
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(courseLearnerProfile).then(
            (courseLearnerProfile) => {
                // update profile with response from server
                this.courseLearnerProfiles[this.activeCourse] = courseLearnerProfile;
                this.aimForGradeOrBonusState.set(EditStateTransition.Saved);
            },
            // Notify user of failure to update
            (res: HttpErrorResponse) => this.onCourseLearnerProfileUpdateError(res, this.aimForGradeOrBonusState),
        );
    }

    updateTimeInvestment(timeInvestment: number) {
        //return if value is changed without user trying to save
        if (!this.courseLearnerProfiles || this.timeInvestmentState() != EditStateTransition.TrySave) {
            return;
        }

        // Try to update profile
        const courseLearnerProfile = this.courseLearnerProfiles[this.activeCourse];
        courseLearnerProfile.timeInvestment = timeInvestment;
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(courseLearnerProfile).then(
            (courseLearnerProfile) => {
                // update profile with response from server
                this.courseLearnerProfiles[this.activeCourse] = courseLearnerProfile;
                this.timeInvestmentState.set(EditStateTransition.Saved);
            },
            // Notify user of failure to update
            (res: HttpErrorResponse) => this.onCourseLearnerProfileUpdateError(res, this.timeInvestmentState),
        );
    }

    updateRepetitionIntensity(repetitionIntensity: number) {
        //return if value is changed without user trying to save
        if (!this.courseLearnerProfiles || this.repetitionIntensityState() != EditStateTransition.TrySave) {
            return;
        }

        // Try to update profile
        const courseLearnerProfile = this.courseLearnerProfiles[this.activeCourse];
        courseLearnerProfile.repetitionIntensity = repetitionIntensity;
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(courseLearnerProfile).then(
            (courseLearnerProfile) => {
                // update profile with response from server
                this.courseLearnerProfiles[this.activeCourse] = courseLearnerProfile;
                this.repetitionIntensityState.set(EditStateTransition.Saved);
            },
            // Notify user of failure to update
            (res: HttpErrorResponse) => this.onCourseLearnerProfileUpdateError(res, this.repetitionIntensityState),
        );
    }

    async loadProfiles() {
        this.courseLearnerProfiles = await this.learnerProfileAPIService.getCourseLearnerProfilesForCurrentUser();
    }

    loadCourses() {
        this.courses = [];
        //iterat eover each course ID in courseLearnerProfiles map to retrieve course title
        Object.keys(this.courseLearnerProfiles).forEach((course) => {
            // course is guaranteed to be int, as this.courseLearnerProfiles has type Record<number, ... >
            this.courseManagementService.find(parseInt(course)).subscribe({
                next: (res: HttpResponse<Course>) => {
                    if (!res.body) {
                        return;
                    }
                    this.courses.push(res.body);
                },
            });
        });
    }
}
