import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TumUiConfirmationService } from '@tumaet/ui-angular';
import { ScienceSettingsComponent } from 'app/account/user/settings/science-settings/science-settings.component';
import { ScienceCourseConsent, ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';

describe('ScienceSettingsComponent', () => {
    let fixture: ComponentFixture<ScienceSettingsComponent>;
    let component: ScienceSettingsComponent;
    let scienceSettingsService: ScienceSettingsService;
    let alertService: AlertService;

    const activeConsent: ScienceCourseConsent = {
        courseId: 1,
        courseTitle: 'Course 1',
        courseShortName: 'C1',
        active: true,
        scienceEnabled: true,
    };

    const undecidedConsent: ScienceCourseConsent = {
        courseId: 2,
        courseTitle: 'Course 2',
        courseShortName: 'C2',
        scienceEnabled: true,
    };

    let consentUpdates: BehaviorSubject<ScienceCourseConsent[]>;
    let featureActive: BehaviorSubject<boolean>;

    beforeEach(async () => {
        consentUpdates = new BehaviorSubject<ScienceCourseConsent[]>([]);
        featureActive = new BehaviorSubject<boolean>(true);

        await TestBed.configureTestingModule({
            imports: [ScienceSettingsComponent],
            providers: [
                MockProvider(ScienceSettingsService),
                MockProvider(AlertService),
                MockProvider(FeatureToggleService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        scienceSettingsService = TestBed.inject(ScienceSettingsService);
        alertService = TestBed.inject(AlertService);
        const featureToggleService = TestBed.inject(FeatureToggleService);

        vi.spyOn(scienceSettingsService, 'getScienceSettingsUpdates').mockReturnValue(consentUpdates.asObservable());
        vi.spyOn(scienceSettingsService, 'refreshScienceSettings').mockReturnValue(of([]));
        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(featureActive.asObservable());

        fixture = TestBed.createComponent(ScienceSettingsComponent);
        component = fixture.componentInstance;
    });

    it('should publish consents from the shared cache', () => {
        fixture.detectChanges();
        consentUpdates.next([activeConsent, undecidedConsent]);

        expect(component.consents()).toEqual([activeConsent, undecidedConsent]);
        expect(component.loading()).toBe(false);
    });

    it('should subscribe to the updates stream only once across feature toggle emissions', () => {
        fixture.detectChanges();
        featureActive.next(false);
        featureActive.next(true);

        expect(scienceSettingsService.getScienceSettingsUpdates).toHaveBeenCalledOnce();
    });

    it('should not load consents while the science feature is disabled', () => {
        featureActive.next(false);
        fixture.detectChanges();

        expect(component.scienceFeatureActive()).toBe(false);
        expect(scienceSettingsService.refreshScienceSettings).not.toHaveBeenCalled();
    });

    it('should report a failed load with the error text', () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(scienceSettingsService, 'refreshScienceSettings').mockReturnValue(throwError(() => new Error('boom')));

        fixture.detectChanges();

        expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: 'boom' });
        expect(component.loading()).toBe(false);
    });

    it('should store the state the switch moved to, not the inverse of a possibly stale row', () => {
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(of(activeConsent));
        fixture.detectChanges();
        consentUpdates.next([activeConsent]);

        component.toggleConsent(activeConsent, false);

        expect(scienceSettingsService.saveConsentForCourse).toHaveBeenCalledWith(activeConsent.courseId, false);
        expect(component.consents()[0].active).toBe(false);
    });

    it('should treat an undecided course as an opt-in when toggled on', () => {
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(of(undecidedConsent));
        fixture.detectChanges();
        consentUpdates.next([undecidedConsent]);

        component.toggleConsent(undecidedConsent, true);

        expect(scienceSettingsService.saveConsentForCourse).toHaveBeenCalledWith(undecidedConsent.courseId, true);
    });

    it('should re-read the stored decision and report the error when the save fails', () => {
        // Not reverted from memory: a withdrawal stores the decision before recording it, so a failure can still have
        // changed the stored state, and putting the row back would show consent the server does not have.
        const alertSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(throwError(() => new Error('nope')));
        fixture.detectChanges();
        consentUpdates.next([activeConsent]);
        const refreshesBefore = vi.mocked(scienceSettingsService.refreshScienceSettings).mock.calls.length;

        component.toggleConsent(activeConsent, false);

        expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: 'nope' });
        expect(vi.mocked(scienceSettingsService.refreshScienceSettings).mock.calls.length).toBe(refreshesBefore + 1);
    });

    it('should not delete science data before the confirmation is accepted', () => {
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(of(undefined));
        fixture.detectChanges();

        component.deleteData(activeConsent);

        expect(scienceSettingsService.deleteScienceDataForCourse).not.toHaveBeenCalled();
    });

    it('should delete science data and reload the consents once confirmed', () => {
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(of(undefined));
        fixture.detectChanges();
        const refreshCallsBeforeDelete = vi.mocked(scienceSettingsService.refreshScienceSettings).mock.calls.length;

        component.deleteData(activeConsent);
        fixture.debugElement.injector.get(TumUiConfirmationService).request(undefined)!.accept();

        expect(scienceSettingsService.deleteScienceDataForCourse).toHaveBeenCalledWith(activeConsent.courseId);
        expect(vi.mocked(scienceSettingsService.refreshScienceSettings).mock.calls.length).toBe(refreshCallsBeforeDelete + 1);
    });

    it('should report a failed deletion with the error text', () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(throwError(() => new Error('denied')));
        fixture.detectChanges();

        component.deleteData(activeConsent);
        fixture.debugElement.injector.get(TumUiConfirmationService).request(undefined)!.accept();

        expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: 'denied' });
    });
});
