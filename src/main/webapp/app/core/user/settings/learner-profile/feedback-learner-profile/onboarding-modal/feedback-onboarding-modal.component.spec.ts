import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackOnboardingModalComponent } from './feedback-onboarding-modal.component';
import { LearnerProfileApiService } from '../../learner-profile-api.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { LearnerProfileDTO } from '../../dto/learner-profile-dto.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';

class MockAlertService {
    addAlert = vi.fn();
    closeAll = vi.fn();
}

describe('FeedbackOnboardingModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FeedbackOnboardingModalComponent;
    let fixture: ComponentFixture<FeedbackOnboardingModalComponent>;
    let learnerProfileApiService: LearnerProfileApiService;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackOnboardingModalComponent, TranslateModule.forRoot()],
            providers: [MockProvider(LearnerProfileApiService), { provide: AlertService, useClass: MockAlertService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackOnboardingModalComponent);
        component = fixture.componentInstance;
        learnerProfileApiService = TestBed.inject(LearnerProfileApiService);
        alertService = TestBed.inject(AlertService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.selected).toEqual([undefined, undefined]);
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
        expect(component.selected[0]).toBeUndefined();
        component.select(1, 0);
        expect(component.selected[1]).toBe(0);
        component.select(1, 1);
        expect(component.selected[1]).toBe(1);
    });

    it('should close the modal by setting visible to false', () => {
        component.visible.set(true);
        component.close();
        expect(component.visible()).toBe(false);
    });

    describe('finish', () => {
        it('should PUT updated profile and emit completed', async () => {
            component.selected = [1, 0];
            const completedSpy = vi.fn();
            component.completed.subscribe(completedSpy);
            const getSpy = vi.spyOn(learnerProfileApiService, 'getLearnerProfileForCurrentUser').mockResolvedValue(new LearnerProfileDTO({ id: 42 }));
            const putSpy = vi.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockResolvedValue(new LearnerProfileDTO({}));
            component.visible.set(true);
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
            expect(completedSpy).toHaveBeenCalledOnce();
            expect(component.visible()).toBe(false);
        });

        it('should handle non-HTTP error and close modal', async () => {
            component.selected = [undefined, undefined];
            vi.spyOn(learnerProfileApiService, 'putUpdatedLearnerProfile').mockRejectedValue(new Error('fail'));
            component.visible.set(true);
            await component.finish();
            expect(alertService.addAlert).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
            expect(component.onboardingCompleted()).toBeUndefined();
            expect(component.visible()).toBe(false);
        });
    });
});
