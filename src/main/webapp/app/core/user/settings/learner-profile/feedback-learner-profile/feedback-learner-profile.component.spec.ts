import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackLearnerProfileComponent } from './feedback-learner-profile.component';
import { LearnerProfileApiService } from '../learner-profile-api.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileDTO } from '../dto/learner-profile-dto.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';

describe('FeedbackLearnerProfileComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FeedbackLearnerProfileComponent;
    let fixture: ComponentFixture<FeedbackLearnerProfileComponent>;
    let learnerProfileApiService: LearnerProfileApiService;
    let alertService: AlertService;

    const mockProfile = new LearnerProfileDTO({
        id: 1,
        feedbackDetail: 1,
        feedbackFormality: 3,
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackLearnerProfileComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                MockProvider(LearnerProfileApiService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackLearnerProfileComponent);
        component = fixture.componentInstance;
        learnerProfileApiService = TestBed.inject(LearnerProfileApiService);
        alertService = TestBed.inject(AlertService);

        vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(mockProfile);
        vi.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(mockProfile);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.learnerProfile()).toEqual(mockProfile);
    });

    describe('Making put requests', () => {
        it('should update profile on successful request', async () => {
            // Arrange
            const newProfile = new LearnerProfileDTO({
                id: mockProfile.id,
                feedbackDetail: 2,
                feedbackFormality: 3,
            });

            component.learnerProfile.set(mockProfile);
            component.disabled = false;
            component.feedbackDetail.set(newProfile.feedbackDetail);
            component.feedbackFormality.set(newProfile.feedbackFormality);

            const putSpy = vi.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(newProfile);
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            const closeAllSpy = vi.spyOn(alertService, 'closeAll');

            // Act
            await component.onToggleChange();
            await fixture.whenStable();
            fixture.detectChanges();

            // Assert
            expect(putSpy).toHaveBeenCalledWith(newProfile);
            expect(component.learnerProfile()).toEqual(newProfile);
            expect(closeAllSpy).toHaveBeenCalled();
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
            });
        });

        it('should error on bad request', async () => {
            // Arrange
            const newProfile = new LearnerProfileDTO({
                id: mockProfile.id,
                feedbackDetail: 2,
                feedbackFormality: 4,
            });

            // Set initial state
            component.learnerProfile.set(mockProfile);
            component.disabled = false;

            // Set new values
            component.feedbackDetail.set(newProfile.feedbackDetail);
            component.feedbackFormality.set(newProfile.feedbackFormality);

            // Mock the API call to fail
            const putSpy = vi.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(new Error('Bad Request'));

            // Act
            await component.onToggleChange();
            await fixture.whenStable();
            fixture.detectChanges();

            // Assert
            expect(putSpy).toHaveBeenCalledWith(newProfile);
            // The profile should remain unchanged
            expect(component.learnerProfile()).toEqual(mockProfile);
            // The signals should keep their new values since the component doesn't reset them on error
            expect(component.feedbackDetail()).toBe(newProfile.feedbackDetail);
            expect(component.feedbackFormality()).toBe(newProfile.feedbackFormality);
        });
    });

    it('should not update profile if no profile exists', async () => {
        component.learnerProfile.set(undefined);
        await component.onToggleChange();
        await fixture.whenStable();

        expect(learnerProfileApiService.putUpdatedLearnerProfile).not.toHaveBeenCalled();
    });

    describe('Error handling', () => {
        beforeEach(() => {
            // Reset the component state
            component.learnerProfile.set(undefined);
            component.disabled = true;
            // Clear any existing mocks
            vi.clearAllMocks();
        });

        it('should handle HTTP errors with specific message', async () => {
            // Arrange
            const httpError = new HttpErrorResponse({
                error: 'Server Error',
                status: 500,
                statusText: 'Internal Server Error',
            });
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(httpError);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toBeUndefined();
            expect(component.disabled).toBeTruthy();
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Http failure response for (unknown url): 500 Internal Server Error',
            });
        });

        it('should handle non-HTTP errors with generic message', async () => {
            // Arrange
            const genericError = new Error('Network error');
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(genericError);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toBeUndefined();
            expect(component.disabled).toBeTruthy();
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
        });

        it('should handle HTTP errors during profile update', async () => {
            // Arrange
            component.learnerProfile.set(mockProfile);
            component.disabled = false;
            const httpError = new HttpErrorResponse({
                error: 'Update Failed',
                status: 500,
                statusText: 'Internal Server Error',
            });
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            vi.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(httpError);

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toEqual(mockProfile); // Profile should remain unchanged
            expect(component.disabled).toBeFalsy(); // Component should remain enabled
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Http failure response for (unknown url): 500 Internal Server Error',
            });
        });
    });

    it('should update all profile values when profile is loaded', async () => {
        // Arrange
        const newProfile = new LearnerProfileDTO({
            id: 1,
            feedbackDetail: 1,
            feedbackFormality: 3,
        });

        // Mock the API to return our new profile
        vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(newProfile);

        // Act
        await component.ngOnInit();
        await fixture.whenStable();

        // Assert
        expect(component.feedbackDetail()).toBe(newProfile.feedbackDetail);
        expect(component.feedbackFormality()).toBe(newProfile.feedbackFormality);
    });

    it('should handle profile with undefined values', async () => {
        // Arrange
        const profileWithUndefinedValues = new LearnerProfileDTO({
            id: 1,
            feedbackDetail: undefined,
            feedbackFormality: undefined,
        });

        vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(profileWithUndefinedValues);

        // Act
        await component.ngOnInit();
        await fixture.whenStable();

        // Assert - Should set default values since DTO constructor converts undefined to DEFAULT_VALUE
        expect(component.feedbackDetail()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
        expect(component.feedbackFormality()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
    });

    describe('Profile validation', () => {
        it('should handle successful profile update with success alert', async () => {
            // Arrange
            const newProfile = new LearnerProfileDTO({
                id: mockProfile.id,
                feedbackDetail: 1,
                feedbackFormality: 3,
            });

            component.learnerProfile.set(mockProfile);
            component.disabled = false;
            component.feedbackDetail.set(newProfile.feedbackDetail);
            component.feedbackFormality.set(newProfile.feedbackFormality);

            const putSpy = vi.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(newProfile);
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            const closeAllSpy = vi.spyOn(alertService, 'closeAll');

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(putSpy).toHaveBeenCalledWith(newProfile);
            expect(component.learnerProfile()).toEqual(newProfile);
            expect(closeAllSpy).toHaveBeenCalled();

            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
            });
        });
    });

    describe('Profile initialization', () => {
        it('should initialize with profile values when profile exists', async () => {
            // Arrange
            const profile = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: 1,
                feedbackFormality: 3,
            });
            vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(profile);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.feedbackDetail()).toBe(profile.feedbackDetail);
            expect(component.feedbackFormality()).toBe(profile.feedbackFormality);
            expect(component.disabled).toBeFalsy();
        });
    });

    describe('Edge cases', () => {
        it('should test onToggleChange with undefined profile', async () => {
            // Arrange
            component.learnerProfile.set(undefined);
            component.disabled = false;

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(learnerProfileApiService.putUpdatedLearnerProfile).not.toHaveBeenCalled();
        });

        it('should test onToggleChange with null profile', async () => {
            // Arrange
            component.learnerProfile.set(null as any);
            component.disabled = false;

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(learnerProfileApiService.putUpdatedLearnerProfile).not.toHaveBeenCalled();
        });

        it('should test component disabled state changes', () => {
            // Arrange & Act
            component.disabled = false;
            expect(component.disabled).toBeFalsy();

            component.disabled = true;
            expect(component.disabled).toBeTruthy();
        });
    });
});
