import { KeyValuePipe, NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { CourseLearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { DoubleSliderComponent } from 'app/shared/double-slider/double-slider.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertService, AlertType } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    styleUrls: ['./course-learner-profile.component.scss'],
    imports: [DoubleSliderComponent, TranslateDirective, NgClass, ArtemisTranslatePipe, FaIconComponent, HelpIconComponent, KeyValuePipe],
})
export class CourseLearnerProfileComponent implements OnInit {
    private alertService = inject(AlertService);
    private learnerProfileAPIService = inject(LearnerProfileApiService);
    protected translateService = inject(TranslateService);

    /**
     * Minimum value allowed for profile fields representing values on a Likert scale.
     * Must be the same as in the server in CourseLearnerProfile.java
     */
    MIN_PROFILE_VALUE = 1;

    /**
     * Maximum value allowed for profile fields representing values on a Likert scale.
     * Must be the same as in the server in CourseLearnerProfile.java
     */
    MAX_PROFILE_VALUE = 5;

    faSave = faSave;

    courseLearnerProfiles: CourseLearnerProfileDTO[];
    activeCourse: number;

    disabled = true;
    editing = false;
    aimForGradeOrBonus = signal<number>(1);
    timeInvestment = signal<number>(1);
    repetitionIntensity = signal<number>(1);

    initialAimForGradeOrBonus = 1;
    initialTimeInvestment = 1;
    initialRepetitionIntensity = 1;

    async ngOnInit() {
        await this.loadProfiles();
    }

    courseChanged(event: Event) {
        const courseId: string = (<HTMLSelectElement>event.target).value;
        this.editing = false;

        // courseId of -1 represents no course selected
        if (courseId !== '-1') {
            this.activeCourse = Number(courseId);
            this.disabled = false;
            const courseLearnerProfile = this.getCourseLearnerProfile(this.activeCourse);
            if (!courseLearnerProfile) {
                return;
            }

            this.updateProfileValues(courseLearnerProfile);
        } else {
            this.disabled = true;
        }
    }

    updateProfileValues(courseLearnerProfile: CourseLearnerProfileDTO) {
        // Update displayed values to new course
        this.initialAimForGradeOrBonus = courseLearnerProfile.aimForGradeOrBonus;
        this.initialTimeInvestment = courseLearnerProfile.timeInvestment;
        this.initialRepetitionIntensity = courseLearnerProfile.repetitionIntensity;
        // update signals
        this.aimForGradeOrBonus.set(courseLearnerProfile.aimForGradeOrBonus);
        this.timeInvestment.set(courseLearnerProfile.timeInvestment);
        this.repetitionIntensity.set(courseLearnerProfile.repetitionIntensity);
    }

    getCourseLearnerProfile(courseId: number): CourseLearnerProfileDTO | undefined {
        return this.courseLearnerProfiles.find((courseLearnerProfile) => {
            if (courseLearnerProfile.courseId === courseId) {
                return courseLearnerProfile;
            }
        });
    }

    update() {
        const courseLearnerProfile = this.getCourseLearnerProfile(this.activeCourse);
        if (!courseLearnerProfile) {
            return;
        }
        courseLearnerProfile.aimForGradeOrBonus = this.aimForGradeOrBonus();
        courseLearnerProfile.timeInvestment = this.timeInvestment();
        courseLearnerProfile.repetitionIntensity = this.repetitionIntensity();

        // Try to update profile
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(courseLearnerProfile).then(
            (courseLearnerProfile) => {
                // update profile with response from server
                const index = this.courseLearnerProfiles.findIndex((profile) => profile.id === courseLearnerProfile.id);
                this.courseLearnerProfiles[index] = courseLearnerProfile;
                this.updateProfileValues(courseLearnerProfile);

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
}
