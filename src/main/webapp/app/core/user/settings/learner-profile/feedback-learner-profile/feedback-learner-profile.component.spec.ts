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

        jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(mockProfile);
        jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(mockProfile);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        jest.restoreAllMocks();
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

            const putSpy = jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(newProfile);
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            const closeAllSpy = jest.spyOn(alertService, 'closeAll');

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
            const putSpy = jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(new Error('Bad Request'));

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
            jest.clearAllMocks();
        });

        it('should handle HTTP errors with specific message', async () => {
            // Arrange
            const httpError = new HttpErrorResponse({
                error: 'Server Error',
                status: 500,
                statusText: 'Internal Server Error',
            });
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(httpError);

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
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(genericError);

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
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(httpError);

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
        jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(newProfile);

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

        jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(profileWithUndefinedValues);

        // Act
        await component.ngOnInit();
        await fixture.whenStable();

        // Assert - Should set default values since DTO constructor converts undefined to DEFAULT_VALUE
        expect(component.feedbackDetail()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
        expect(component.feedbackFormality()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
    });

    it('should handle non-HTTP error during profile update', async () => {
        // Arrange
        component.learnerProfile.set(mockProfile);
        component.disabled = false;
        const genericError = new Error('Network error');
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(genericError);

        // Act
        await component.onToggleChange();
        await fixture.whenStable();

        // Assert
        expect(component.learnerProfile()).toEqual(mockProfile); // Profile should remain unchanged
        expect(component.disabled).toBeFalsy(); // Component should remain enabled
        expect(addAlertSpy).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
        });
    });

    it('should handle HTTP error with specific message during profile update', async () => {
        // Arrange
        component.learnerProfile.set(mockProfile);
        component.disabled = false;
        const httpError = new HttpErrorResponse({
            error: 'Validation failed',
            status: 400,
            statusText: 'Bad Request',
        });
        const addAlertSpy = jest.spyOn(alertService, 'addAlert');
        jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(httpError);

        // Act
        await component.onToggleChange();
        await fixture.whenStable();

        // Assert
        expect(component.learnerProfile()).toEqual(mockProfile); // Profile should remain unchanged
        expect(component.disabled).toBeFalsy(); // Component should remain enabled
        expect(addAlertSpy).toHaveBeenCalledWith({
            type: AlertType.DANGER,
            message: 'Http failure response for (unknown url): 400 Bad Request',
        });
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

            const putSpy = jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(newProfile);
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            const closeAllSpy = jest.spyOn(alertService, 'closeAll');

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

        it('should update profile values in component state after successful update', async () => {
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

            jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(newProfile);

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(component.feedbackDetail()).toBe(newProfile.feedbackDetail);
            expect(component.feedbackFormality()).toBe(newProfile.feedbackFormality);
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
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(profile);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.feedbackDetail()).toBe(profile.feedbackDetail);
            expect(component.feedbackFormality()).toBe(profile.feedbackFormality);
            expect(component.disabled).toBeFalsy();
        });

        it('should update profile values when profile is set', async () => {
            // Arrange - Create a new component instance to avoid conflicts with beforeEach
            const newFixture = TestBed.createComponent(FeedbackLearnerProfileComponent);
            const newComponent = newFixture.componentInstance;
            const newLearnerProfileApiService = TestBed.inject(LearnerProfileApiService);

            const testProfile = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: 1,
                feedbackFormality: 3,
            });

            jest.spyOn(newLearnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(testProfile);

            // Act - Initialize the new component
            await newComponent.ngOnInit();
            await newFixture.whenStable();

            // Assert
            expect(newComponent.feedbackDetail()).toBe(1);
            expect(newComponent.feedbackFormality()).toBe(3);
        });

        it('should load profile and update component state through ngOnInit', async () => {
            // Arrange
            const testProfile = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: 1,
                feedbackFormality: 3,
            });
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(testProfile);

            // Act - This calls loadProfile internally
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toEqual(testProfile);
            expect(component.disabled).toBeFalsy();
            expect(component.feedbackDetail()).toBe(1);
            expect(component.feedbackFormality()).toBe(3);
        });

        it('should handle errors during profile loading through ngOnInit', async () => {
            // Arrange - Create a new component instance to avoid conflicts with beforeEach
            const newFixture = TestBed.createComponent(FeedbackLearnerProfileComponent);
            const newComponent = newFixture.componentInstance;
            const newLearnerProfileApiService = TestBed.inject(LearnerProfileApiService);

            const error = new Error('Load failed');
            jest.spyOn(newLearnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(error);

            // Act - This calls loadProfile internally which calls handleError on failure
            await newComponent.ngOnInit();
            await newFixture.whenStable();

            // Assert - Component should remain in error state
            expect(newComponent.learnerProfile()).toBeUndefined();
            expect(newComponent.disabled).toBeTruthy();
        });

        it('should handle component initialization with no profile', async () => {
            // Arrange - Create a new component instance
            const newFixture = TestBed.createComponent(FeedbackLearnerProfileComponent);
            const newComponent = newFixture.componentInstance;
            const newLearnerProfileApiService = TestBed.inject(LearnerProfileApiService);

            // Mock the API call to reject (simulate no profile found)
            jest.spyOn(newLearnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(new Error('Profile not found'));

            // Act - Initialize the new component
            await newComponent.ngOnInit();
            await newFixture.whenStable();

            // Assert - Component should remain in initial state
            expect(newComponent.learnerProfile()).toBeUndefined();
            expect(newComponent.disabled).toBeTruthy();
        });
    });

    describe('Private method testing', () => {
        it('should test loadProfile method through ngOnInit', async () => {
            // Arrange
            const testProfile = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: 1,
                feedbackFormality: 3,
            });
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(testProfile);

            // Act - Call ngOnInit which internally calls loadProfile
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toEqual(testProfile);
            expect(component.disabled).toBeFalsy();
            expect(component.feedbackDetail()).toBe(1);
            expect(component.feedbackFormality()).toBe(3);
        });

        it('should test loadProfile error handling through ngOnInit', async () => {
            // Arrange
            const error = new Error('Load failed');
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(error);
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which internally calls loadProfile and handleError
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
        });

        it('should test updateProfileValues method through profile loading', async () => {
            // Arrange
            const testProfile = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: 1,
                feedbackFormality: 3,
            });
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(testProfile);

            // Act - Load profile which internally calls updateProfileValues
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.feedbackDetail()).toBe(1);
            expect(component.feedbackFormality()).toBe(3);
        });

        it('should test updateProfileValues method with undefined values through profile loading', async () => {
            // Arrange
            const testProfile = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: undefined,
                feedbackFormality: undefined,
            });
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(testProfile);

            // Act - Load profile which internally calls updateProfileValues
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert - Should set default values since DTO constructor converts undefined to DEFAULT_VALUE
            expect(component.feedbackDetail()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
            expect(component.feedbackFormality()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
        });

        it('should test handleError method with HttpErrorResponse through API failure', async () => {
            // Arrange
            const httpError = new HttpErrorResponse({
                error: 'Test error',
                status: 500,
                statusText: 'Internal Server Error',
            });
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(httpError);
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which will trigger handleError
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'Http failure response for (unknown url): 500 Internal Server Error',
            });
        });

        it('should test handleError method with generic error through API failure', async () => {
            // Arrange
            const genericError = new Error('Generic error');
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(genericError);
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which will trigger handleError
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
        });

        it('should test handleError method with null error through API failure', async () => {
            // Arrange
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(null);
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which will trigger handleError
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
        });

        it('should test handleError method with string error through API failure', async () => {
            // Arrange
            const stringError = 'String error';
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(stringError);
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');

            // Act - Call ngOnInit which will trigger handleError
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(addAlertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
        });
    });

    describe('Edge cases and additional coverage', () => {
        it('should handle profile with null values', async () => {
            // Arrange
            const profileWithNullValues = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: null as any,
                feedbackFormality: null as any,
            });

            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(profileWithNullValues);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert - Should set default values since DTO constructor converts null to DEFAULT_VALUE
            expect(component.feedbackDetail()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
            expect(component.feedbackFormality()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
        });

        it('should handle profile with zero values', async () => {
            // Arrange
            const profileWithZeroValues = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: 0,
                feedbackFormality: 0,
            });

            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(profileWithZeroValues);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert - Should set default values since DTO constructor converts out-of-range values (0 < MIN_VALUE) to DEFAULT_VALUE
            expect(component.feedbackDetail()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
            expect(component.feedbackFormality()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
        });

        it('should handle profile with maximum values', async () => {
            // Arrange
            const profileWithMaxValues = new LearnerProfileDTO({
                id: 1,
                feedbackDetail: 5,
                feedbackFormality: 5,
            });

            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(profileWithMaxValues);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.feedbackDetail()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
            expect(component.feedbackFormality()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
        });

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

        it('should test component with different signal values', () => {
            // Arrange
            component.feedbackDetail.set(1);
            component.feedbackFormality.set(3);

            // Act & Assert
            expect(component.feedbackDetail()).toBe(1);
            expect(component.feedbackFormality()).toBe(3);
        });

        it('should test component disabled state changes', () => {
            // Arrange & Act
            component.disabled = false;
            expect(component.disabled).toBeFalsy();

            component.disabled = true;
            expect(component.disabled).toBeTruthy();
        });

        it('should test component with empty profile object', async () => {
            // Arrange
            const emptyProfile = new LearnerProfileDTO({});
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(emptyProfile);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toEqual(emptyProfile);
            expect(component.disabled).toBeFalsy();
        });
    });
});
