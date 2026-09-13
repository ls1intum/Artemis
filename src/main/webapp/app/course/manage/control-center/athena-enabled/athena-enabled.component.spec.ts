import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { provideRouter } from '@angular/router';
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
            providers: [provideRouter([]), MockProvider(AthenaCourseConfigService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
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
        expect(comp.masterEnabled()).toBe(true);
    });

    it('should show disabled while the configuration is still loading', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));
        componentRef.setInput('course', course);

        expect(comp.masterEnabled()).toBe(false);
    });

    describe('setEnabled', () => {
        it('should turn both features on when switched on', () => {
            initWith(bothDisabled);
            const updateSpy = vi
                .spyOn(athenaCourseConfigService, 'updateCourseConfig')
                .mockReturnValue(of(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true } })));

            comp.setEnabled(true);

            expect(updateSpy).toHaveBeenCalledWith(5, { gradingFeedbackEnabled: true });
            expect(updateSpy).toHaveBeenCalledWith(5, { formativeFeedbackEnabled: true });
            expect(comp.masterEnabled()).toBe(true);
        });

        it('should turn both features off when switched off', () => {
            initWith({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: true });
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: bothDisabled })));

            comp.setEnabled(false);

            expect(updateSpy).toHaveBeenCalledWith(5, { gradingFeedbackEnabled: false });
            expect(updateSpy).toHaveBeenCalledWith(5, { formativeFeedbackEnabled: false });
            expect(comp.masterEnabled()).toBe(false);
        });

        it('should do nothing when the course has no id', () => {
            const courseWithoutId = new Course();
            componentRef.setInput('course', courseWithoutId);
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig');

            comp.setEnabled(true);

            expect(updateSpy).not.toHaveBeenCalled();
        });
    });

    describe('settingsRoute', () => {
        it('should return the correct route for the course settings page', () => {
            componentRef.setInput('course', course);

            expect(comp.settingsRoute()).toEqual(['/course-management', '5', 'athena-settings']);
        });
    });

    describe('when the course changes', () => {
        const otherCourse = new Course();
        otherCourse.id = 6;

        it('should load the configuration of the new course', () => {
            initWith({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
            const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));

            componentRef.setInput('course', otherCourse);
            fixture.detectChanges();

            expect(getSpy).toHaveBeenCalledTimes(2);
            expect(getSpy).toHaveBeenLastCalledWith(6);
            expect(comp.masterEnabled()).toBe(false);
        });
    });

    it('should render the toggle and the configure link', () => {
        initWith(bothDisabled);

        const element = fixture.nativeElement;
        expect(element.querySelector('[data-testid="athena-enabled-enable"]')).toBeTruthy();
        expect(element.querySelector('[data-testid="athena-enabled-disable"]')).toBeTruthy();
        expect(element.querySelector('.athena-configure-link')).toBeTruthy();
    });

    it('should render a loading placeholder instead of the toggle before the configuration answers', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(new Subject<AthenaCourseConfigDTO>().asObservable());
        componentRef.setInput('course', course);
        fixture.detectChanges();

        const element = fixture.nativeElement;
        expect(element.querySelector('[data-testid="athena-enabled-loading"]')).toBeTruthy();
        expect(element.querySelector('[data-testid="athena-enabled-enable"]')).toBeFalsy();
        // The configure link is not gated on the load: it only navigates, so there is nothing there to get wrong.
        expect(element.querySelector('.athena-configure-link')).toBeTruthy();
    });

    it('should save the switched state when the toggle button is clicked', () => {
        initWith(bothDisabled);
        const updateSpy = vi
            .spyOn(athenaCourseConfigService, 'updateCourseConfig')
            .mockReturnValue(of(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true } })));

        fixture.nativeElement.querySelector('[data-testid="athena-enabled-enable"]').click();

        expect(updateSpy).toHaveBeenCalledWith(5, { gradingFeedbackEnabled: true });
        expect(updateSpy).toHaveBeenCalledWith(5, { formativeFeedbackEnabled: true });
    });
});
