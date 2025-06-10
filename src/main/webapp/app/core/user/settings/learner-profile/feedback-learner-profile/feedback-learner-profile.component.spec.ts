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

describe('FeedbackLearnerProfileComponent', () => {
    let component: FeedbackLearnerProfileComponent;
    let fixture: ComponentFixture<FeedbackLearnerProfileComponent>;
    let learnerProfileApiService: LearnerProfileApiService;

    const mockProfile = new LearnerProfileDTO({
        id: 1,
        feedbackAlternativeStandard: 2,
        feedbackFollowupSummary: 2,
        feedbackBriefDetailed: 2,
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackLearnerProfileComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(LearnerProfileApiService), provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackLearnerProfileComponent);
        component = fixture.componentInstance;
        learnerProfileApiService = TestBed.inject(LearnerProfileApiService);

        jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(mockProfile);
        jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile');

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
                feedbackAlternativeStandard: 1,
                feedbackFollowupSummary: 3,
                feedbackBriefDetailed: 2,
            });

            component.learnerProfile.set(mockProfile);
            component.disabled = false;
            component.feedbackAlternativeStandard.set(newProfile.feedbackAlternativeStandard);
            component.feedbackFollowupSummary.set(newProfile.feedbackFollowupSummary);
            component.feedbackBriefDetailed.set(newProfile.feedbackBriefDetailed);

            const putSpy = jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(newProfile);

            // Act
            await component.onToggleChange();
            await fixture.whenStable();
            fixture.detectChanges();

            // Assert
            expect(putSpy).toHaveBeenCalledWith(newProfile);
            expect(component.learnerProfile()).toEqual(newProfile);
        });

        it('should error on bad request', async () => {
            // Arrange
            const newProfile = new LearnerProfileDTO({
                id: mockProfile.id,
                feedbackAlternativeStandard: 1,
                feedbackFollowupSummary: 3,
                feedbackBriefDetailed: 2,
            });

            // Set initial state
            component.learnerProfile.set(mockProfile);
            component.disabled = false;

            // Set new values
            component.feedbackAlternativeStandard.set(newProfile.feedbackAlternativeStandard);
            component.feedbackFollowupSummary.set(newProfile.feedbackFollowupSummary);
            component.feedbackBriefDetailed.set(newProfile.feedbackBriefDetailed);

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
            expect(component.feedbackAlternativeStandard()).toBe(newProfile.feedbackAlternativeStandard);
            expect(component.feedbackFollowupSummary()).toBe(newProfile.feedbackFollowupSummary);
            expect(component.feedbackBriefDetailed()).toBe(newProfile.feedbackBriefDetailed);
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
            const httpError = new HttpErrorResponse({ error: 'Server Error', status: 500 });
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(httpError);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toBeUndefined();
            expect(component.disabled).toBeTrue();
        });

        it('should handle non-HTTP errors with generic message', async () => {
            // Arrange
            const genericError = new Error('Network error');
            jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockRejectedValue(genericError);

            // Act
            await component.ngOnInit();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toBeUndefined();
            expect(component.disabled).toBeTrue();
        });

        it('should handle HTTP errors during profile update', async () => {
            // Arrange
            component.learnerProfile.set(mockProfile);
            component.disabled = false;
            const httpError = new HttpErrorResponse({ error: 'Update Failed', status: 500 });
            jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(httpError);

            // Act
            await component.onToggleChange();
            await fixture.whenStable();

            // Assert
            expect(component.learnerProfile()).toEqual(mockProfile); // Profile should remain unchanged
            expect(component.disabled).toBeFalse(); // Component should remain enabled
        });
    });

    it('should update all profile values when profile is loaded', async () => {
        // Arrange
        const newProfile = new LearnerProfileDTO({
            id: 1,
            feedbackAlternativeStandard: 3,
            feedbackFollowupSummary: 1,
            feedbackBriefDetailed: 2,
        });

        // Mock the API to return our new profile
        jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(newProfile);

        // Act
        await component.ngOnInit();
        await fixture.whenStable();

        // Assert
        expect(component.feedbackAlternativeStandard()).toBe(newProfile.feedbackAlternativeStandard);
        expect(component.feedbackFollowupSummary()).toBe(newProfile.feedbackFollowupSummary);
        expect(component.feedbackBriefDetailed()).toBe(newProfile.feedbackBriefDetailed);
    });

    it('should handle profile with undefined values', async () => {
        // Arrange
        const profileWithUndefinedValues = new LearnerProfileDTO({
            id: 1,
            feedbackAlternativeStandard: undefined,
            feedbackFollowupSummary: undefined,
            feedbackBriefDetailed: undefined,
        });

        jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(profileWithUndefinedValues);

        // Act
        await component.ngOnInit();
        await fixture.whenStable();

        // Assert
        expect(component.feedbackAlternativeStandard()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
        expect(component.feedbackFollowupSummary()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
        expect(component.feedbackBriefDetailed()).toBe(LearnerProfileDTO.DEFAULT_VALUE);
    });

    it('should handle non-HTTP error during profile update', async () => {
        // Arrange
        component.learnerProfile.set(mockProfile);
        component.disabled = false;
        const genericError = new Error('Network error');
        jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(genericError);

        // Act
        await component.onToggleChange();
        await fixture.whenStable();

        // Assert
        expect(component.learnerProfile()).toEqual(mockProfile); // Profile should remain unchanged
        expect(component.disabled).toBeFalse(); // Component should remain enabled
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
        jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(httpError);

        // Act
        await component.onToggleChange();
        await fixture.whenStable();

        // Assert
        expect(component.learnerProfile()).toEqual(mockProfile); // Profile should remain unchanged
        expect(component.disabled).toBeFalse(); // Component should remain enabled
    });
});
