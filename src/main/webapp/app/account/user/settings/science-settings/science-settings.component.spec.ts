import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TumAetUiConfirmationService } from '@tumaet/ui-angular';
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

    let featureActive: BehaviorSubject<boolean>;

    const query = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

    beforeEach(async () => {
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

        vi.spyOn(scienceSettingsService, 'getConsents').mockReturnValue(of([activeConsent, undecidedConsent]));
        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(featureActive.asObservable());

        fixture = TestBed.createComponent(ScienceSettingsComponent);
        component = fixture.componentInstance;
    });

    it('should show the consents the page loaded itself', () => {
        fixture.detectChanges();

        expect(scienceSettingsService.getConsents).toHaveBeenCalledOnce();
        expect(component.consents()).toEqual([activeConsent, undecidedConsent]);
        expect(component.loading()).toBe(false);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="science-consent-row"]')).toHaveLength(2);
    });

    it('should show nothing but the spinner until its own response arrived', () => {
        const pendingLoad = new Subject<ScienceCourseConsent[]>();
        vi.spyOn(scienceSettingsService, 'getConsents').mockReturnValue(pendingLoad.asObservable());

        fixture.detectChanges();
        expect(query('science-consent-loading')).not.toBeNull();
        expect(query('science-consent-list')).toBeNull();
        expect(query('science-consent-empty')).toBeNull();

        pendingLoad.next([]);
        fixture.detectChanges();
        expect(query('science-consent-loading')).toBeNull();
        expect(query('science-consent-empty')).not.toBeNull();
    });

    it('should keep only the latest load when an earlier one answers late', () => {
        const firstLoad = new Subject<ScienceCourseConsent[]>();
        const secondLoad = new Subject<ScienceCourseConsent[]>();
        vi.spyOn(scienceSettingsService, 'getConsents').mockReturnValueOnce(firstLoad.asObservable()).mockReturnValueOnce(secondLoad.asObservable());
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(of(undefined));
        fixture.detectChanges();

        // The deletion reloads while the first load is still running.
        component.deleteData(activeConsent);
        fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)!.accept();
        firstLoad.next([activeConsent]);
        expect(component.loading()).toBe(true);

        secondLoad.next([undecidedConsent]);
        expect(component.consents()).toEqual([undecidedConsent]);
        expect(component.loading()).toBe(false);
    });

    it('should not let a list read from before a save overwrite the decision it stored', () => {
        // A deletion in one course reloads the list while a decision in another course is being stored. The reload may
        // read the decision before the save committed; answering after the save, it would show the old decision.
        const staleLoad = new Subject<ScienceCourseConsent[]>();
        const freshLoad = new Subject<ScienceCourseConsent[]>();
        const pendingSave = new Subject<ScienceCourseConsent>();
        vi.spyOn(scienceSettingsService, 'getConsents')
            .mockReturnValueOnce(of([activeConsent, undecidedConsent]))
            .mockReturnValueOnce(staleLoad.asObservable())
            .mockReturnValueOnce(freshLoad.asObservable());
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(pendingSave.asObservable());
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(of(undefined));
        fixture.detectChanges();

        component.toggleConsent(undecidedConsent, true);
        component.deleteData(activeConsent);
        fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)!.accept();
        pendingSave.next({ ...undecidedConsent, active: true });
        pendingSave.complete();

        // The read started before the save answered is dropped, and the list is read again.
        staleLoad.next([activeConsent, undecidedConsent]);
        expect(staleLoad.observed).toBe(false);
        expect(scienceSettingsService.getConsents).toHaveBeenCalledTimes(3);
        freshLoad.next([activeConsent, { ...undecidedConsent, active: true }]);
        expect(component.consents()[1].active).toBe(true);
        expect(component.loading()).toBe(false);
    });

    it('should ignore a load that answers after the page was destroyed', () => {
        const pendingLoad = new Subject<ScienceCourseConsent[]>();
        vi.spyOn(scienceSettingsService, 'getConsents').mockReturnValue(pendingLoad.asObservable());
        const alertSpy = vi.spyOn(alertService, 'error');
        fixture.detectChanges();

        fixture.destroy();
        pendingLoad.error(new Error('late'));

        expect(pendingLoad.observed).toBe(false);
        expect(component.consents()).toEqual([]);
        expect(alertSpy).not.toHaveBeenCalled();
    });

    it('should ignore a deletion that answers after the page was destroyed', () => {
        const pendingDelete = new Subject<void>();
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(pendingDelete.asObservable());
        const successSpy = vi.spyOn(alertService, 'success');
        fixture.detectChanges();
        component.deleteData(activeConsent);
        fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)!.accept();

        fixture.destroy();
        pendingDelete.next();

        expect(successSpy).not.toHaveBeenCalled();
        expect(scienceSettingsService.getConsents).toHaveBeenCalledOnce();
    });

    it('should not load consents while the science feature is disabled', () => {
        featureActive.next(false);
        fixture.detectChanges();

        expect(component.scienceFeatureActive()).toBe(false);
        expect(component.loading()).toBe(false);
        expect(scienceSettingsService.getConsents).not.toHaveBeenCalled();
        expect(query('science-consent-feature-disabled')).not.toBeNull();
    });

    it('should load the consents once the feature is switched on', () => {
        featureActive.next(false);
        fixture.detectChanges();

        featureActive.next(true);

        expect(scienceSettingsService.getConsents).toHaveBeenCalledOnce();
        expect(component.consents()).toEqual([activeConsent, undecidedConsent]);
    });

    it('should report a failed load and still show the list after a later reload', () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(scienceSettingsService, 'getConsents')
            .mockReturnValueOnce(throwError(() => new Error('boom')))
            .mockReturnValueOnce(of([activeConsent]));

        fixture.detectChanges();
        expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: 'boom' });
        expect(component.loading()).toBe(false);

        featureActive.next(false);
        featureActive.next(true);
        fixture.detectChanges();

        expect(component.consents()).toEqual([activeConsent]);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="science-consent-row"]')).toHaveLength(1);
    });

    it('should store the state the switch moved to, not the inverse of a possibly stale row', () => {
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(of({ ...activeConsent, active: false }));
        fixture.detectChanges();

        component.toggleConsent(activeConsent, false);

        expect(scienceSettingsService.saveConsentForCourse).toHaveBeenCalledWith(activeConsent.courseId, false);
        expect(component.consents()[0].active).toBe(false);
    });

    it('should treat an undecided course as an opt-in when toggled on', () => {
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(of({ ...undecidedConsent, active: true }));
        fixture.detectChanges();

        component.toggleConsent(undecidedConsent, true);

        expect(scienceSettingsService.saveConsentForCourse).toHaveBeenCalledWith(undecidedConsent.courseId, true);
    });

    it('should lock the switch of a course while its decision is being stored', () => {
        // Two overlapping saves can reach the server in either order, so a quick on-off could leave the student
        // contributing while the switch shows off.
        const pendingSave = new Subject<ScienceCourseConsent>();
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(pendingSave);
        fixture.detectChanges();

        component.toggleConsent(undecidedConsent, true);
        fixture.detectChanges();
        const toggles = fixture.nativeElement.querySelectorAll('[data-testid="science-consent-toggle"] input');
        expect(toggles[1].disabled).toBe(true);
        // Another course stays usable.
        expect(toggles[0].disabled).toBe(false);

        component.toggleConsent(undecidedConsent, false);
        expect(scienceSettingsService.saveConsentForCourse).toHaveBeenCalledOnce();

        // The switch shows the decision the server confirmed, and is unlocked again.
        pendingSave.next({ ...undecidedConsent, active: true });
        pendingSave.complete();
        fixture.detectChanges();
        expect(component.consents()[1].active).toBe(true);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="science-consent-toggle"] input')[1].disabled).toBe(false);
    });

    it('should re-read the stored decision and report the error when the save fails', () => {
        // Not reverted from memory: a withdrawal stores the decision before recording it, so a failure can still have
        // changed the stored state, and putting the row back would show consent the server does not have.
        const alertSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(throwError(() => new Error('nope')));
        fixture.detectChanges();

        component.toggleConsent(activeConsent, false);

        expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: 'nope' });
        expect(scienceSettingsService.getConsents).toHaveBeenCalledTimes(2);
        expect(component.consents()[0].active).toBe(true);
    });

    it('should not delete science data before the confirmation is accepted', () => {
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(of(undefined));
        fixture.detectChanges();

        component.deleteData(activeConsent);

        expect(scienceSettingsService.deleteScienceDataForCourse).not.toHaveBeenCalled();
    });

    it('should delete science data and reload the consents once confirmed', () => {
        const successSpy = vi.spyOn(alertService, 'success');
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(of(undefined));
        fixture.detectChanges();

        component.deleteData(activeConsent);
        fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)!.accept();

        expect(scienceSettingsService.deleteScienceDataForCourse).toHaveBeenCalledWith(activeConsent.courseId);
        expect(scienceSettingsService.getConsents).toHaveBeenCalledTimes(2);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.userSettings.scienceSettingsPage.deleteDataSuccess', { courseTitle: activeConsent.courseTitle });
    });

    it('should report a failed deletion with the error text', () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        const successSpy = vi.spyOn(alertService, 'success');
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(throwError(() => new Error('denied')));
        fixture.detectChanges();

        component.deleteData(activeConsent);
        fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)!.accept();

        expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: 'denied' });
        expect(successSpy).not.toHaveBeenCalled();
    });

    it('should name each switch after its course, not just "Contribute"', () => {
        fixture.detectChanges();

        const labelledBy = fixture.nativeElement.querySelector(`#science-course-${activeConsent.courseId}`).getAttribute('aria-labelledby');
        expect(labelledBy).toContain(`science-course-title-${activeConsent.courseId}`);
    });
});
