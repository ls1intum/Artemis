import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { CourseLearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { SegmentedToggleComponent } from 'app/shared/segmented-toggle/segmented-toggle.component';
import { COURSE_LEARNER_PROFILE_OPTIONS } from './learner-profile-options';

@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    styleUrls: ['./course-learner-profile.component.scss'],
    imports: [TranslateDirective, NgClass, ArtemisTranslatePipe, HelpIconComponent, SegmentedToggleComponent],
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

    courseLearnerProfiles: CourseLearnerProfileDTO[] = []; // initialize empty array to avoid undefined error
    activeCourseId: number;

    disabled = true;

    // Use the shared options for all toggles
    protected aimForGradeOrBonusOptions = COURSE_LEARNER_PROFILE_OPTIONS;
    protected timeInvestmentOptions = COURSE_LEARNER_PROFILE_OPTIONS;
    protected repetitionIntensityOptions = COURSE_LEARNER_PROFILE_OPTIONS;

    aimForGradeOrBonus = signal<string>('1');
    timeInvestment = signal<string>('1');
    repetitionIntensity = signal<string>('1');

    initialAimForGradeOrBonus = '1';
    initialTimeInvestment = '1';
    initialRepetitionIntensity = '1';

    async ngOnInit() {
        await this.loadProfiles();
    }

    courseChanged(event: Event) {
        const courseId: string = (<HTMLSelectElement>event.target).value;

        // courseId of -1 represents no course selected
        if (courseId !== '-1') {
            this.activeCourseId = Number(courseId);
            this.disabled = false;
            const courseLearnerProfile = this.getCourseLearnerProfile(this.activeCourseId);
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
        this.initialAimForGradeOrBonus = courseLearnerProfile.aimForGradeOrBonus.toString();
        this.initialTimeInvestment = courseLearnerProfile.timeInvestment.toString();
        this.initialRepetitionIntensity = courseLearnerProfile.repetitionIntensity.toString();
        // update signals
        this.aimForGradeOrBonus.set(courseLearnerProfile.aimForGradeOrBonus.toString());
        this.timeInvestment.set(courseLearnerProfile.timeInvestment.toString());
        this.repetitionIntensity.set(courseLearnerProfile.repetitionIntensity.toString());
    }

    getCourseLearnerProfile(courseId: number): CourseLearnerProfileDTO | undefined {
        return this.courseLearnerProfiles.find((courseLearnerProfile) => {
            if (courseLearnerProfile.courseId === courseId) {
                return courseLearnerProfile;
            }
        });
    }

    onToggleChange() {
        const courseLearnerProfile = this.getCourseLearnerProfile(this.activeCourseId);
        if (!courseLearnerProfile) {
            return;
        }
        courseLearnerProfile.aimForGradeOrBonus = Number(this.aimForGradeOrBonus());
        courseLearnerProfile.timeInvestment = Number(this.timeInvestment());
        courseLearnerProfile.repetitionIntensity = Number(this.repetitionIntensity());

        // Try to update profile
        this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(courseLearnerProfile).then(
            (courseLearnerProfile) => {
                // update profile with response from server
                const index = this.courseLearnerProfiles.findIndex((profile) => profile.id === courseLearnerProfile.id);
                this.courseLearnerProfiles[index] = courseLearnerProfile;
                this.updateProfileValues(courseLearnerProfile);

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
