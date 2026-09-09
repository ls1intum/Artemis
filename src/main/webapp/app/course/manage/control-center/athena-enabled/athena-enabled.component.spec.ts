import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { AthenaEnabledComponent } from 'app/course/manage/control-center/athena-enabled/athena-enabled.component';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { MockProvider } from 'ng-mocks';

describe('AthenaEnabledComponent', () => {
    let comp: AthenaEnabledComponent;
    let componentRef: ComponentRef<AthenaEnabledComponent>;
    let fixture: ComponentFixture<AthenaEnabledComponent>;
    let athenaCourseConfigService: AthenaCourseConfigService;

    const course = new Course();
    course.id = 5;

    const bothDisabled: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };

    function initWith(config: AthenaCourseConfigDTO) {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(config));
        componentRef.setInput('course', course);
        fixture.detectChanges();
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AthenaEnabledComponent, TranslatePipeMock],
            providers: [MockProvider(AthenaCourseConfigService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(AthenaEnabledComponent);
        comp = fixture.componentInstance;
        componentRef = fixture.componentRef;
        athenaCourseConfigService = TestBed.inject(AthenaCourseConfigService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load the course configuration on init', () => {
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }));
        componentRef.setInput('course', course);
        fixture.detectChanges();

        expect(getSpy).toHaveBeenCalledExactlyOnceWith(5);
        expect(comp.gradingEnabled()).toBe(true);
        expect(comp.formativeEnabled()).toBe(false);
    });

    it('should show both features as disabled and alert when the configuration cannot be loaded', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
        componentRef.setInput('course', course);
        fixture.detectChanges();

        expect(comp.config()).toBeUndefined();
        expect(comp.gradingEnabled()).toBe(false);
        expect(comp.formativeEnabled()).toBe(false);
        expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
    });

    it('should still save a switched feature when no configuration was loaded', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        componentRef.setInput('course', course);
        fixture.detectChanges();
        const expected = { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false };
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: expected })));

        comp.setEnabled('gradingFeedbackEnabled', true);

        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { gradingFeedbackEnabled: true });
        expect(comp.gradingEnabled()).toBe(true);
    });

    it.each([
        { feature: 'formativeFeedbackEnabled' as const, expected: { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true } },
        { feature: 'gradingFeedbackEnabled' as const, expected: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } },
    ])('should save $feature without touching the other feature', ({ feature, expected }) => {
        initWith(bothDisabled);
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: expected })));

        comp.setEnabled(feature, true);

        // Only the switched feature is sent: restating the other one would write back the value this component last
        // read, undoing a change made elsewhere in the meantime.
        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { [feature]: true });
        expect(comp.config()).toEqual(expected);
    });

    it('should keep a feature switched while the configuration was still loading', () => {
        // The load is answered only after the instructor has already switched a feature. Applying it then would put
        // the state the server held before the switch back on screen.
        const load = new Subject<AthenaCourseConfigDTO>();
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(load.asObservable());
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(new Subject<never>().asObservable());
        componentRef.setInput('course', course);
        fixture.detectChanges();

        comp.setEnabled('gradingFeedbackEnabled', true);
        load.next(bothDisabled);

        expect(comp.gradingEnabled()).toBe(true);
    });

    it('should only revert the feature whose save failed', () => {
        initWith(bothDisabled);
        const formative = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValueOnce(formative.asObservable()).mockReturnValueOnce(new Subject<never>().asObservable());

        comp.setEnabled('formativeFeedbackEnabled', true);
        comp.setEnabled('gradingFeedbackEnabled', true);
        formative.error(new HttpErrorResponse({ status: 400 }));

        // Restoring the whole snapshot this switch was clicked on would also drop the grading switch made after it.
        expect(comp.config()).toEqual({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
    });

    it('should not send a request when the feature already has the requested state', () => {
        initWith({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig');

        comp.setEnabled('gradingFeedbackEnabled', true);

        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('should revert the toggle and alert when saving fails', () => {
        initWith(bothDisabled);
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        comp.setEnabled('gradingFeedbackEnabled', true);

        expect(comp.gradingEnabled()).toBe(false);
        expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
    });

    it('should render a toggle pair per feature', () => {
        initWith(bothDisabled);

        const element = fixture.nativeElement;
        expect(element.querySelector('[data-testid="athena-formative-feedback-enable"]')).toBeTruthy();
        expect(element.querySelector('[data-testid="athena-formative-feedback-disable"]')).toBeTruthy();
        expect(element.querySelector('[data-testid="athena-grading-feedback-enable"]')).toBeTruthy();
        expect(element.querySelector('[data-testid="athena-grading-feedback-disable"]')).toBeTruthy();
    });

    it('should mark the button matching the stored state as active', () => {
        initWith({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });

        const element = fixture.nativeElement;
        expect(element.querySelector('[data-testid="athena-grading-feedback-enable"]').classList).toContain('enabled-toggle-btn--active-on');
        expect(element.querySelector('[data-testid="athena-grading-feedback-disable"]').classList).not.toContain('enabled-toggle-btn--active-off');
        expect(element.querySelector('[data-testid="athena-formative-feedback-disable"]').classList).toContain('enabled-toggle-btn--active-off');
    });

    it('should save the feature when its toggle button is clicked', () => {
        initWith(bothDisabled);
        const updateSpy = vi
            .spyOn(athenaCourseConfigService, 'updateCourseConfig')
            .mockReturnValue(of(new HttpResponse({ body: { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true } })));

        fixture.nativeElement.querySelector('[data-testid="athena-formative-feedback-enable"]').click();

        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { formativeFeedbackEnabled: true });
    });
});
