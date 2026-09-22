import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminScienceComponent } from 'app/admin/science/admin-science.component';
import { AdminScienceService } from 'app/admin/science/admin-science.service';
import { ScienceEnabledCourse } from 'app/admin/science/admin-science.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AdminScienceComponent', () => {
    let fixture: ComponentFixture<AdminScienceComponent>;
    let component: AdminScienceComponent;
    let adminScienceService: AdminScienceService;
    let alertService: AlertService;

    const enabledCourse: ScienceEnabledCourse = { courseId: 1, courseTitle: 'Course 1', courseShortName: 'C1', active: true };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AdminScienceComponent],
            providers: [MockProvider(AdminScienceService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        adminScienceService = TestBed.inject(AdminScienceService);
        alertService = TestBed.inject(AlertService);

        vi.spyOn(adminScienceService, 'getCourses').mockReturnValue(of([enabledCourse]));
        vi.spyOn(adminScienceService, 'getExportAudits').mockReturnValue(of([]));
        vi.spyOn(adminScienceService, 'getSelectableCourses').mockReturnValue(of([{ id: 1, title: 'Course 1', shortName: 'C1', semester: 'WS26' }]));

        fixture = TestBed.createComponent(AdminScienceComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('should offer every course as a labelled option rather than as a bare id', () => {
        fixture.detectChanges();

        expect(component.selectableCourseOptions()).toEqual([{ id: 1, label: 'Course 1 (C1) WS26 #1' }]);
    });

    it('should load the activation history and the export audits on init', () => {
        fixture.detectChanges();

        expect(component.courses()).toEqual([enabledCourse]);
        expect(component.audits()).toEqual([]);
        expect(component.loading()).toBe(false);
    });

    it('should report a failed course load with the error text', () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(adminScienceService, 'getCourses').mockReturnValue(throwError(() => new Error('boom')));

        fixture.detectChanges();

        expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: 'boom' });
        expect(component.loading()).toBe(false);
    });

    describe('enableCourse', () => {
        it('should do nothing without a course id', () => {
            vi.spyOn(adminScienceService, 'enableCourse').mockReturnValue(of(enabledCourse));
            fixture.detectChanges();

            component.enableCourse();

            expect(adminScienceService.enableCourse).not.toHaveBeenCalled();
        });

        it('should clear the input and reload after a successful enable', () => {
            vi.spyOn(adminScienceService, 'enableCourse').mockReturnValue(of(enabledCourse));
            fixture.detectChanges();
            component.courseIdToEnable.set(42);

            component.enableCourse();

            expect(adminScienceService.enableCourse).toHaveBeenCalledWith(42);
            expect(component.courseIdToEnable()).toBeUndefined();
            expect(adminScienceService.getCourses).toHaveBeenCalledTimes(2);
        });

        it('should keep the input and report the error when enabling fails', () => {
            const alertSpy = vi.spyOn(alertService, 'error');
            vi.spyOn(adminScienceService, 'enableCourse').mockReturnValue(throwError(() => new Error('nope')));
            fixture.detectChanges();
            component.courseIdToEnable.set(42);

            component.enableCourse();

            expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: 'nope' });
            expect(component.courseIdToEnable()).toBe(42);
        });
    });

    it('should reload after disabling a course', () => {
        vi.spyOn(adminScienceService, 'disableCourse').mockReturnValue(of({ ...enabledCourse, active: false }));
        fixture.detectChanges();

        component.disableCourse(enabledCourse);

        expect(adminScienceService.disableCourse).toHaveBeenCalledWith(enabledCourse.courseId);
        expect(adminScienceService.getCourses).toHaveBeenCalledTimes(2);
    });

    it('should narrow the export filter when an event type checkbox is clicked', async () => {
        // Deliberately driven through the DOM rather than by calling toggleEventType: the binding was wired to an
        // output the component does not have, so the box moved while the filter did not - and a filter the form does
        // not reflect is exactly how an administrator ends up exporting more than they asked for.
        fixture.detectChanges();
        // ngModel writes into the control in a microtask, so the box is not actually ticked until this settles.
        await fixture.whenStable();
        fixture.detectChanges();
        const excluded = component.eventTypes[0];

        fixture.debugElement.queryAll(By.css('[data-testid="science-event-type"] input'))[0].nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedEventTypes()).not.toContain(excluded);
        expect(component.selectedEventTypes()).toHaveLength(component.eventTypes.length - 1);
    });

    it('should select every event type up front so the form shows what will be exported', () => {
        fixture.detectChanges();

        expect(component.selectedEventTypes()).toEqual(component.eventTypes);
    });

    it('should report the reason the server gave rather than a transport failure', () => {
        const alertSpy = vi.spyOn(alertService, 'error');
        // The name and the already-prefixed value are what HeaderUtil.createFailureAlert actually writes; reading the
        // success-alert header instead made this whole path a no-op while the test still passed.
        const error = new HttpErrorResponse({ status: 400, headers: new HttpHeaders({ 'X-artemisApp-error': 'error.scienceExportInvalidDateRange' }) });
        vi.spyOn(adminScienceService, 'createExport').mockReturnValue(throwError(() => error));
        fixture.detectChanges();
        component.exportCourseIdsToInclude.set([1]);
        component.exportPurpose.set('Study');

        component.createExport();

        expect(alertSpy).toHaveBeenCalledWith('error.scienceExportInvalidDateRange');
    });

    it('should add and remove event types from the export filter', () => {
        fixture.detectChanges();
        component.selectedEventTypes.set([]);

        component.toggleEventType(ScienceEventType.LECTURE__OPEN, true);
        component.toggleEventType(ScienceEventType.EXERCISE__OPEN, true);
        expect(component.selectedEventTypes()).toEqual([ScienceEventType.LECTURE__OPEN, ScienceEventType.EXERCISE__OPEN]);

        component.toggleEventType(ScienceEventType.LECTURE__OPEN, false);
        expect(component.selectedEventTypes()).toEqual([ScienceEventType.EXERCISE__OPEN]);

        // A repeat of the same state must not list the type twice: the filter is what the export is audited by.
        component.toggleEventType(ScienceEventType.EXERCISE__OPEN, true);
        expect(component.selectedEventTypes()).toEqual([ScienceEventType.EXERCISE__OPEN]);
    });

    describe('createExport', () => {
        beforeEach(() => {
            vi.spyOn(adminScienceService, 'createExport').mockReturnValue(of(new Blob(['identity\n'])));
            fixture.detectChanges();
        });

        it.each([
            ['no courses selected', [] as number[], 'Study'],
            ['a blank purpose', [1], '   '],
        ])('should refuse an export with %s', (_case, courseIds, purpose) => {
            const alertSpy = vi.spyOn(alertService, 'error');
            component.exportCourseIdsToInclude.set(courseIds);
            component.exportPurpose.set(purpose);

            component.createExport();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.admin.science.export.validation');
            expect(adminScienceService.createExport).not.toHaveBeenCalled();
        });

        it('should select a course for export when its checkbox is clicked', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();

            fixture.debugElement.queryAll(By.css('[data-testid="science-export-course"] input'))[0].nativeElement.click();
            fixture.detectChanges();

            expect(component.exportCourseIdsToInclude()).toEqual([enabledCourse.courseId]);
        });

        it('should refuse an export that asks for no event types', () => {
            // The server reads an absent type filter as every type, so sending an empty one would be the broadest
            // export rather than the narrowest.
            const alertSpy = vi.spyOn(alertService, 'error');
            component.exportCourseIdsToInclude.set([1]);
            component.exportPurpose.set('Study');
            component.selectedEventTypes.set([]);

            component.createExport();

            // Its own message: the course and the purpose are both filled in, so the generic one would be misleading.
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.admin.science.export.validationEventTypes');
            expect(adminScienceService.createExport).not.toHaveBeenCalled();
        });

        it('should select and deselect courses without listing one twice', () => {
            component.toggleExportCourse(1, true);
            component.toggleExportCourse(2, true);
            component.toggleExportCourse(1, true);
            expect(component.exportCourseIdsToInclude()).toEqual([2, 1]);

            component.toggleExportCourse(2, false);
            expect(component.exportCourseIdsToInclude()).toEqual([1]);
        });

        it('should send the parsed filter and download the returned file', () => {
            const createObjectURL = vi.fn().mockReturnValue('blob:science');
            const revokeObjectURL = vi.fn();
            vi.stubGlobal('URL', { ...window.URL, createObjectURL, revokeObjectURL });
            const anchor = document.createElement('a');
            const click = vi.spyOn(anchor, 'click').mockImplementation(() => undefined);
            vi.spyOn(document, 'createElement').mockReturnValue(anchor);

            component.exportCourseIdsToInclude.set([1, 2]);
            component.exportPurpose.set('Study');
            component.exportFrom.set('2026-01-01T00:00');
            component.selectedEventTypes.set([ScienceEventType.LECTURE__OPEN]);

            component.createExport();

            expect(adminScienceService.createExport).toHaveBeenCalledWith({
                courseIds: [1, 2],
                from: new Date('2026-01-01T00:00').toISOString(),
                to: undefined,
                eventTypes: [ScienceEventType.LECTURE__OPEN],
                purpose: 'Study',
            });
            expect(click).toHaveBeenCalledOnce();
            expect(anchor.download).toBe('science-research-export.csv');
            expect(revokeObjectURL).toHaveBeenCalledWith('blob:science');
        });

        it('should report a failed export with the error text', () => {
            const alertSpy = vi.spyOn(alertService, 'error');
            vi.spyOn(adminScienceService, 'createExport').mockReturnValue(throwError(() => new Error('500')));
            component.exportCourseIdsToInclude.set([1]);
            component.exportPurpose.set('Study');

            component.createExport();

            expect(alertSpy).toHaveBeenCalledWith('error.unexpectedError', { error: '500' });
        });
    });
});
