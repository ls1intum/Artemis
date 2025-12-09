import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileApiService } from 'app/core/user/settings/learner-profile/learner-profile-api.service';
import { CourseLearnerProfileDTO } from 'app/core/user/settings/learner-profile/dto/course-learner-profile-dto.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { SegmentedToggleComponent } from 'app/shared/segmented-toggle/segmented-toggle.component';
import { COURSE_LEARNER_PROFILE_OPTIONS } from 'app/core/user/settings/learner-profile/entities/course-learner-profile-options.model';

/**
 * Component for managing course-specific learner profiles.
 * This component allows users to view and modify their learning preferences for specific courses,
 * including their aim for grades/bonus, time investment, and repetition intensity.
 */
@Component({
    selector: 'jhi-course-learner-profile',
    templateUrl: './course-learner-profile.component.html',
    styleUrls: ['../learner-profile.component.scss'],
    imports: [TranslateDirective, NgClass, SegmentedToggleComponent],
})
export class CourseLearnerProfileComponent implements OnInit {
    private readonly alertService = inject(AlertService);
    private readonly learnerProfileAPIService = inject(LearnerProfileApiService);
    protected readonly translateService = inject(TranslateService);

    /** Signal containing the list of course learner profiles for the current user */
    public readonly courseLearnerProfiles = signal<CourseLearnerProfileDTO[]>([]);

    /** Currently selected course ID, null if no course is selected */
    activeCourseId: number | null = null;

    /** Flag indicating whether the profile editing is disabled */
    disabled = true;

    /**
     * Options mapped from shared options with translated labels.
     */
    protected readonly aimForGradeOrBonusOptions = COURSE_LEARNER_PROFILE_OPTIONS.map((option) => ({
        label: this.translateService.instant(option.translationKey),
        value: option.level,
    }));
    protected readonly timeInvestmentOptions = COURSE_LEARNER_PROFILE_OPTIONS.map((option) => ({
        label: this.translateService.instant(option.translationKey),
        value: option.level,
    }));
    protected readonly repetitionIntensityOptions = COURSE_LEARNER_PROFILE_OPTIONS.map((option) => ({
        label: this.translateService.instant(option.translationKey),
        value: option.level,
    }));

    /** Default value for profile settings */
    private readonly defaultProfileValue = CourseLearnerProfileDTO.MIN_VALUE;

    /** Signals for course learner profile settings */
    aimForGradeOrBonus = signal<number>(this.defaultProfileValue);
    timeInvestment = signal<number>(this.defaultProfileValue);
    repetitionIntensity = signal<number>(this.defaultProfileValue);

    async ngOnInit(): Promise<void> {
        await this.loadProfiles();
    }

    /**
     * Handles course selection change event.
     * Updates the active course and loads its profile if a course is selected.
     * @param event - The change event from the course selection dropdown
     */
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
        this.loadProfileForCourse(this.activeCourseId);
    }

    /**
     * Loads the learner profile for a specific course.
     * @param courseId - The ID of the course to load the profile for
     */
    private loadProfileForCourse(courseId: number): void {
        const courseLearnerProfile = this.getCourseLearnerProfile(courseId);
        if (!courseLearnerProfile) {
            return;
        }

        this.updateProfileValues(courseLearnerProfile);
    }

    /**
     * Updates the profile values in the component's signals.
     * @param courseLearnerProfile - The course learner profile containing the values to update
     */
    private updateProfileValues(courseLearnerProfile: CourseLearnerProfileDTO): void {
        this.aimForGradeOrBonus.set(courseLearnerProfile.aimForGradeOrBonus);
        this.timeInvestment.set(courseLearnerProfile.timeInvestment);
        this.repetitionIntensity.set(courseLearnerProfile.repetitionIntensity);
    }

    /**
     * Retrieves the learner profile for a specific course.
     * @param courseId - The ID of the course to get the profile for
     * @returns The course learner profile or undefined if not found
     */
    private getCourseLearnerProfile(courseId: number): CourseLearnerProfileDTO | undefined {
        return this.courseLearnerProfiles().find((profile) => profile.courseId === courseId);
    }

    /**
     * Loads all course learner profiles for the current user.
     * Handles any errors that occur during the loading process.
     */
    private async loadProfiles(): Promise<void> {
        try {
            const profiles = await this.learnerProfileAPIService.getCourseLearnerProfilesForCurrentUser();
            this.courseLearnerProfiles.set(profiles);
        } catch (error) {
            this.handleError(error);
        }
    }

    /**
     * Handles changes to any of the profile toggles.
     * Validates and saves the updated profile if valid.
     */
    async onToggleChange(): Promise<void> {
        if (!this.activeCourseId) return;

        const courseLearnerProfile = this.getCourseLearnerProfile(this.activeCourseId);
        if (!courseLearnerProfile) return;

        // Create a new CourseLearnerProfileDTO object with the updated values
        const updatedProfile = new CourseLearnerProfileDTO();
        Object.assign(
            updatedProfile,
            Object.assign({}, courseLearnerProfile, {
                aimForGradeOrBonus: this.aimForGradeOrBonus(),
                timeInvestment: this.timeInvestment(),
                repetitionIntensity: this.repetitionIntensity(),
            }),
        );

        if (!updatedProfile.isValid()) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.courseLearnerProfile.invalidRange',
            });
            return;
        }

        // Save the updated profile
        try {
            const savedProfile = await this.learnerProfileAPIService.putUpdatedCourseLearnerProfile(updatedProfile);

            // Update the profiles array using signal's update method
            this.courseLearnerProfiles.update((profiles) => profiles.map((profile) => (profile.id === savedProfile.id ? savedProfile : profile)));

            this.updateProfileValues(savedProfile);

            this.alertService.closeAll();
            this.alertService.addAlert({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.courseLearnerProfile.profileSaved',
            });
        } catch (error) {
            this.handleError(error);
        }
    }

    /**
     * Handles errors that occur during API calls or other operations.
     * Displays appropriate error messages to the user.
     * @param error - The error that occurred
     */
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
}
