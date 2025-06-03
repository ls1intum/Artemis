import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackLearnerProfileComponent } from './feedback-learner-profile.component';
import { LearnerProfileApiService } from '../learner-profile-api.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileDTO } from '../dto/learner-profile-dto.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';

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
        component.learnerProfile.set(null);
        await component.onToggleChange();
        await fixture.whenStable();

        expect(learnerProfileApiService.putUpdatedLearnerProfile).not.toHaveBeenCalled();
    });
});
