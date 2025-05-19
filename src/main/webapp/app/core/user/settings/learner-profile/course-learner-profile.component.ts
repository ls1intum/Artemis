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
import { COURSE_LEARNER_PROFILE_OPTIONS } from 'app/learner-profile/shared/entities/learner-profile-options.model';

@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    styleUrls: ['./course-learner-profile.component.scss'],
    imports: [TranslateDirective, NgClass, ArtemisTranslatePipe, HelpIconComponent, SegmentedToggleComponent],
})
export class CourseLearnerProfileComponent implements OnInit {
    private readonly alertService = inject(AlertService);
    private readonly learnerProfileAPIService = inject(LearnerProfileApiService);
    protected readonly translateService = inject(TranslateService);

    courseLearnerProfiles: CourseLearnerProfileDTO[] = [];
    activeCourseId: number | null = null;

    disabled = true;

    // Use the shared options for all toggles
    protected readonly aimForGradeOrBonusOptions = COURSE_LEARNER_PROFILE_OPTIONS;
    protected readonly timeInvestmentOptions = COURSE_LEARNER_PROFILE_OPTIONS;
    protected readonly repetitionIntensityOptions = COURSE_LEARNER_PROFILE_OPTIONS;

    aimForGradeOrBonus = signal<string>('1');
    timeInvestment = signal<string>('1');
    repetitionIntensity = signal<string>('1');

    initialAimForGradeOrBonus = '1';
    initialTimeInvestment = '1';
    initialRepetitionIntensity = '1';

    async ngOnInit(): Promise<void> {
        await this.loadProfiles();
    }

    courseChanged(event: Event): void {
        const select = event.target as HTMLSelectElement;
        const courseId = select.value;

        if (courseId === '-1') {
            this.activeCourseId = null;
            this.disabled = true;
            return;
        }

        this.activeCourseId = Number(courseId);
        this.disabled = false;

        const courseLearnerProfile = this.getCourseLearnerProfile(this.activeCourseId);
        if (!courseLearnerProfile) {
            return;
        }

        this.updateProfileValues(courseLearnerProfile);
    }

    private updateProfileValues(courseLearnerProfile: CourseLearnerProfileDTO): void {
        // Update displayed values to new course
        this.initialAimForGradeOrBonus = courseLearnerProfile.aimForGradeOrBonus.toString();
        this.initialTimeInvestment = courseLearnerProfile.timeInvestment.toString();
        this.initialRepetitionIntensity = courseLearnerProfile.repetitionIntensity.toString();

        // update signals
        this.aimForGradeOrBonus.set(courseLearnerProfile.aimForGradeOrBonus.toString());
        this.timeInvestment.set(courseLearnerProfile.timeInvestment.toString());
        this.repetitionIntensity.set(courseLearnerProfile.repetitionIntensity.toString());
    }

    private getCourseLearnerProfile(courseId: number): CourseLearnerProfileDTO | undefined {
        return this.courseLearnerProfiles.find((profile) => profile.courseId === courseId);
    }

    async onToggleChange(): Promise<void> {
        if (!this.activeCourseId) return;

        const courseLearnerProfile = this.getCourseLearnerProfile(this.activeCourseId);
        if (!courseLearnerProfile) return;

        const updatedProfile = new CourseLearnerProfileDTO();
        Object.assign(updatedProfile, {
            ...courseLearnerProfile,
            aimForGradeOrBonus: Number(this.aimForGradeOrBonus()),
            timeInvestment: Number(this.timeInvestment()),
            repetitionIntensity: Number(this.repetitionIntensity()),
        });

        if (!updatedProfile.isValid()) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: `Values must be between ${CourseLearnerProfileDTO.MIN_VALUE} and ${CourseLearnerProfileDTO.MAX_VALUE}`,
                translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.invalidRange',
            });
            return;
        }

        try {
            const savedProfile = await this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(updatedProfile);

            // Update the profiles array
            const index = this.courseLearnerProfiles.findIndex((profile) => profile.id === savedProfile.id);
            this.courseLearnerProfiles[index] = savedProfile;
            this.updateProfileValues(savedProfile);

            this.alertService.addAlert({
                type: AlertType.SUCCESS,
                message: 'Profile saved',
                translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.profileSaved',
            });
        } catch (error) {
            this.handleError(error);
        }
    }

    private handleError(error: unknown): void {
        let errorMessage: string;

        if (error instanceof HttpErrorResponse) {
            errorMessage = error.error?.title || error.headers?.get('x-artemisapp-alert') || 'An error occurred';
        } else {
            errorMessage = 'An unexpected error occurred';
        }

        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
    }

    private async loadProfiles(): Promise<void> {
        try {
            this.courseLearnerProfiles = await this.learnerProfileAPIService.getCourseLearnerProfilesForCurrentUser();
        } catch (error) {
            this.handleError(error);
        }
    }
}
