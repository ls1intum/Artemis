import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackLearnerProfileComponent } from 'app/core/user/settings/learner-profile/feedback-learner-profile.component';
import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';

describe('FeedbackLearnerProfileComponent', () => {
    let fixture: ComponentFixture<FeedbackLearnerProfileComponent>;
    let component: FeedbackLearnerProfileComponent;
    let httpTesting: HttpTestingController;
    let learnerProfileApiService: LearnerProfileApiService;
    let putUpdatedLearnerProfileSpy: jest.SpyInstance;

    const errorBody = {
        entityName: 'learnerProfile',
        errorKey: 'learnerProfileNotFound',
        type: 'https://www.jhipster.tech/problem/problem-with-message',
        title: 'LearnerProfile not found.',
        status: 400,
        skipAlert: true,
        message: 'error.learnerProfileNotFound',
        params: 'learnerProfile',
    };
    const errorHeaders = {
        'x-artemisapp-error': 'error.learnerProfileNotFound',
        'x-artemisapp-params': 'learnerProfile',
    };

    const mockProfile = {
        id: 1,
        feedbackAlternativeStandard: 2,
        feedbackFollowupSummary: 3,
        feedbackBriefDetailed: 4,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(AlertService),
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        learnerProfileApiService = TestBed.inject(LearnerProfileApiService);
        httpTesting = TestBed.inject(HttpTestingController);

        fixture = TestBed.createComponent(FeedbackLearnerProfileComponent);
        component = fixture.componentInstance;

        jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockReturnValue(new Promise((resolve) => resolve(mockProfile)));

        putUpdatedLearnerProfileSpy = jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.profileId).toBe(mockProfile.id);
        expect(component.feedbackAlternativeStandard()).toBe(mockProfile.feedbackAlternativeStandard);
        expect(component.feedbackFollowupSummary()).toBe(mockProfile.feedbackFollowupSummary);
        expect(component.feedbackBriefDetailed()).toBe(mockProfile.feedbackBriefDetailed);
    });

    function setupUpdateTest(): any {
        const newProfile = {
            id: component.profileId,
            feedbackAlternativeStandard: 3,
            feedbackFollowupSummary: 4,
            feedbackBriefDetailed: 5,
        };

        component.feedbackAlternativeStandard.set(newProfile.feedbackAlternativeStandard);
        component.feedbackFollowupSummary.set(newProfile.feedbackFollowupSummary);
        component.feedbackBriefDetailed.set(newProfile.feedbackBriefDetailed);

        return newProfile;
    }

    async function validateUpdate(profile: any) {
        component.update();
        const req = httpTesting.expectOne(`api/atlas/learner-profiles/${profile.id}`, 'Request to put new Profile');
        req.flush(profile);

        // Wait for the component to update its state
        await fixture.whenStable();
        fixture.detectChanges();

        expect(putUpdatedLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedLearnerProfileSpy.mock.calls[0][0]).toEqual(profile);
        expect(component.initialFeedbackAlternativeStandard).toBe(profile.feedbackAlternativeStandard);
        expect(component.initialFeedbackFollowupSummary).toBe(profile.feedbackFollowupSummary);
        expect(component.initialFeedbackBriefDetailed).toBe(profile.feedbackBriefDetailed);
    }

    async function validateError(profile: any) {
        component.update();
        const req = httpTesting.expectOne(`api/atlas/learner-profiles/${profile.id}`, 'Request to put new Profile');
        req.flush(errorBody, {
            headers: errorHeaders,
            status: 400,
            statusText: 'Bad Request',
        });

        // Wait for the component to update its state
        await fixture.whenStable();
        fixture.detectChanges();

        expect(putUpdatedLearnerProfileSpy).toHaveBeenCalled();
        expect(putUpdatedLearnerProfileSpy.mock.calls[0][0]).toEqual(profile);
        expect(component.initialFeedbackAlternativeStandard).toBe(mockProfile.feedbackAlternativeStandard);
        expect(component.initialFeedbackFollowupSummary).toBe(mockProfile.feedbackFollowupSummary);
        expect(component.initialFeedbackBriefDetailed).toBe(mockProfile.feedbackBriefDetailed);
    }

    describe('Making put requests', () => {
        it('should update profile on successful request', async () => {
            const profile = setupUpdateTest();
            await validateUpdate(profile);
        });

        it('should error on bad request', async () => {
            const profile = setupUpdateTest();
            await validateError(profile);
        });
    });
});
