import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackOnboardingModalComponent } from './feedback-onboarding-modal.component';
import { LearnerProfileApiService } from '../../learner-profile-api.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { LearnerProfileDTO } from '../../dto/learner-profile-dto.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';

class MockActiveModal {
    close = jest.fn();
}
class MockAlertService {
    addAlert = jest.fn();
    closeAll = jest.fn();
}

describe('FeedbackOnboardingModalComponent', () => {
    let component: FeedbackOnboardingModalComponent;
    let fixture: ComponentFixture<FeedbackOnboardingModalComponent>;
    let learnerProfileApiService: LearnerProfileApiService;
    let alertService: AlertService;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackOnboardingModalComponent, TranslateModule.forRoot()],
            providers: [
                MockProvider(LearnerProfileApiService),
                { provide: NgbActiveModal, useClass: MockActiveModal },
                { provide: AlertService, useClass: MockAlertService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackOnboardingModalComponent);
        component = fixture.componentInstance;
        learnerProfileApiService = TestBed.inject(LearnerProfileApiService);
        alertService = TestBed.inject(AlertService);
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.selected).toEqual([null, null]);
        expect(component.step).toBe(0);
    });

    it('should navigate steps with next and back', () => {
        expect(component.step).toBe(0);
        component.next();
        expect(component.step).toBe(1);
        component.next();
        expect(component.step).toBe(1); // should not exceed max
        component.back();
        expect(component.step).toBe(0);
        component.back();
        expect(component.step).toBe(0); // should not go below 0
    });

    it('should select and deselect choices', () => {
        component.select(0, 1);
        expect(component.selected[0]).toBe(1);
        component.select(0, 1);
        expect(component.selected[0]).toBeNull();
        component.select(1, 0);
        expect(component.selected[1]).toBe(0);
        component.select(1, 1);
        expect(component.selected[1]).toBe(1);
    });

    it('should close the modal', () => {
        component.close();
        expect(activeModal.close).toHaveBeenCalled();
    });

    describe('finish', () => {
        it('should POST new profile if profileMissing is true', async () => {
            component.profileMissing.set(true);
            component.selected = [0, 1];
            const postSpy = jest.spyOn(learnerProfileApiService, 'postLearnerProfile').mockResolvedValue(
                new LearnerProfileDTO({
                    feedbackDetail: 1,
                    feedbackFormality: 3,
                    hasSetupFeedbackPreferences: true,
                }),
            );
            await component.finish();
            expect(postSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    feedbackDetail: 1,
                    feedbackFormality: 3,
                    hasSetupFeedbackPreferences: true,
                }),
            );
            expect(alertService.closeAll).toHaveBeenCalled();
            expect(alertService.addAlert).toHaveBeenCalledWith({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
            });
            expect(component.onboardingCompleted()).toBeUndefined();
            expect(activeModal.close).toHaveBeenCalled();
        });

        it('should PUT updated profile if profileMissing is false', async () => {
            component.profileMissing.set(false);
            component.selected = [1, 0];
            const getSpy = jest.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(new LearnerProfileDTO({ id: 42 }));
            const putSpy = jest.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(new LearnerProfileDTO({}));
            await component.finish();
            expect(getSpy).toHaveBeenCalled();
            expect(putSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    id: 42,
                    feedbackDetail: 3,
                    feedbackFormality: 1,
                    hasSetupFeedbackPreferences: true,
                }),
            );
            expect(alertService.closeAll).toHaveBeenCalled();
            expect(alertService.addAlert).toHaveBeenCalledWith({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
            });
            expect(component.onboardingCompleted()).toBeUndefined();
            expect(activeModal.close).toHaveBeenCalled();
        });

        it('should handle error and close modal', async () => {
            component.profileMissing.set(true);
            component.selected = [null, null];
            const error = new HttpErrorResponse({ error: 'fail', status: 500 });
            jest.spyOn(learnerProfileApiService, 'postLearnerProfile').mockRejectedValue(error);
            await component.finish();
            expect(alertService.addAlert).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: error.message,
            });
            expect(component.onboardingCompleted()).toBeUndefined();
            expect(activeModal.close).toHaveBeenCalled();
        });

        it('should handle non-HTTP error and close modal', async () => {
            component.profileMissing.set(true);
            component.selected = [null, null];
            jest.spyOn(learnerProfileApiService, 'postLearnerProfile').mockRejectedValue(new Error('fail'));
            await component.finish();
            expect(alertService.addAlert).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
            expect(component.onboardingCompleted()).toBeUndefined();
            expect(activeModal.close).toHaveBeenCalled();
        });
    });
});
