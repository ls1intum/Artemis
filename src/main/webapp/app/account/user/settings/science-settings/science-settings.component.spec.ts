import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ScienceSettingsComponent } from 'app/account/user/settings/science-settings/science-settings.component';
import { ScienceCourseConsent, ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ScienceSettingsComponent],
            providers: [MockProvider(ScienceSettingsService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ScienceSettingsComponent);
        component = fixture.componentInstance;
        scienceSettingsService = TestBed.inject(ScienceSettingsService);
        alertService = TestBed.inject(AlertService);

        vi.spyOn(scienceSettingsService, 'getScienceSettingsUpdates').mockReturnValue(of([activeConsent]));
        vi.spyOn(scienceSettingsService, 'refreshScienceSettings').mockReturnValue(of([activeConsent]));
    });

    it('loads per-course consents and clears the loading state after refresh', () => {
        component.ngOnInit();

        expect(component.consents()).toEqual([activeConsent]);
        expect(component.loading()).toBe(false);
    });

    it('shows an error and clears loading when refresh fails', () => {
        vi.spyOn(scienceSettingsService, 'refreshScienceSettings').mockReturnValue(throwError(() => new Error('failed')));
        const alertSpy = vi.spyOn(alertService, 'error');

        component.loadConsents();

        expect(component.loading()).toBe(false);
        expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError');
    });

    it('saves the inverted consent value when toggling a course', () => {
        const saveSpy = vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(of({ ...activeConsent, active: false }));
        const alertSpy = vi.spyOn(alertService, 'success');

        component.toggleConsent(activeConsent);

        expect(saveSpy).toHaveBeenCalledWith(activeConsent.courseId, false);
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.userSettings.saveSettingsSuccessAlert');
    });

    it('shows an error when saving consent fails', () => {
        vi.spyOn(scienceSettingsService, 'saveConsentForCourse').mockReturnValue(throwError(() => new Error('failed')));
        const successSpy = vi.spyOn(alertService, 'success');
        const errorSpy = vi.spyOn(alertService, 'error');

        component.toggleConsent(activeConsent);

        expect(successSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledWith('error.unexpectedError');
    });

    it('deletes science data after confirmation', () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        const deleteSpy = vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(of(undefined));
        const alertSpy = vi.spyOn(alertService, 'success');

        component.deleteData(activeConsent);

        expect(deleteSpy).toHaveBeenCalledWith(activeConsent.courseId);
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.userSettings.saveSettingsSuccessAlert');
    });

    it('shows an error when deleting science data fails', () => {
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse').mockReturnValue(throwError(() => new Error('failed')));
        const successSpy = vi.spyOn(alertService, 'success');
        const errorSpy = vi.spyOn(alertService, 'error');

        component.deleteData(activeConsent);

        expect(successSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledWith('error.unexpectedError');
    });

    it('does not delete science data when confirmation is rejected', () => {
        vi.spyOn(window, 'confirm').mockReturnValue(false);
        const deleteSpy = vi.spyOn(scienceSettingsService, 'deleteScienceDataForCourse');

        component.deleteData(activeConsent);

        expect(deleteSpy).not.toHaveBeenCalled();
    });
});
