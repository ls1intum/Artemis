import { Component, OnInit, inject, signal } from '@angular/core';
import { DoubleSliderComponent } from 'app/shared/double-slider/double-slider.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { CourseLearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    styleUrls: ['./course-learner-profile.component.scss'],
    imports: [DoubleSliderComponent, TranslateDirective, NgClass, ArtemisTranslatePipe, FaIconComponent, HelpIconComponent],
})
export class CourseLearnerProfileComponent implements OnInit {
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private learnerProfileAPIService = inject(LearnerProfileApiService);
    protected translateService = inject(TranslateService);

    faSave = faSave;

    courses: Course[];
    courseLearnerProfiles: Record<number, CourseLearnerProfileDTO>;
    activeCourse: number;

    disabled = true;
    editing = false;
    aimForGradeOrBonus = signal<number>(0);
    timeInvestment = signal<number>(0);
    repetitionIntensity = signal<number>(0);
    initialAimForGradeOrBonus = 0;
    initialTimeInvestment = 0;
    initialRepetitionIntensity = 0;

    async ngOnInit() {
        await this.loadProfiles();
        this.loadCourses();
    }

    courseChanged(event: Event) {
        const courseId: string = (<HTMLSelectElement>event.target).value;
        this.editing = false;

        // courseId of -1 represents no course selected
        if (courseId !== '-1') {
            this.activeCourse = Number(courseId);
            this.disabled = false;
            const courseLearnerProfile = this.courseLearnerProfiles[this.activeCourse];

            // Update displayed values to new course
            this.aimForGradeOrBonus.set(courseLearnerProfile.aimForGradeOrBonus);
            this.initialAimForGradeOrBonus = this.aimForGradeOrBonus();
            this.timeInvestment.set(courseLearnerProfile.timeInvestment);
            this.initialTimeInvestment = this.timeInvestment();
            this.repetitionIntensity.set(courseLearnerProfile.repetitionIntensity);
            this.initialRepetitionIntensity = this.repetitionIntensity();
        } else {
            this.disabled = true;
        }
    }

    update() {
        const courseLearnerProfile = this.courseLearnerProfiles[this.activeCourse];
        courseLearnerProfile.aimForGradeOrBonus = this.aimForGradeOrBonus();
        courseLearnerProfile.timeInvestment = this.timeInvestment();
        courseLearnerProfile.repetitionIntensity = this.repetitionIntensity();

        // Try to update profile
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(courseLearnerProfile).then(
            (courseLearnerProfile) => {
                // update profile with response from server
                this.courseLearnerProfiles[this.activeCourse] = courseLearnerProfile;
                this.initialAimForGradeOrBonus = this.aimForGradeOrBonus();
                this.initialTimeInvestment = this.timeInvestment();
                this.initialRepetitionIntensity = this.repetitionIntensity();

                this.editing = false;

                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'Profile saved',
                    translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.profileSaved',
                });
            },
            // Notify user of failure to update
            (error: HttpErrorResponse) => {
                const errorMessage = error.error ? error.error.title : error.headers?.get('x-artemisapp-alert');
                if (errorMessage) {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: errorMessage,
                        disableTranslation: true,
                    });
                }
            },
        );
    }

    async loadProfiles() {
        this.courseLearnerProfiles = await this.learnerProfileAPIService.getCourseLearnerProfilesForCurrentUser();
    }

    loadCourses() {
        this.courses = [];
        //iterate over each course ID in courseLearnerProfiles map to retrieve course title
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
