import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LtiCourseCardComponent } from 'app/lti/manage/lti-course-card/lti-course-card.component';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('LtiCourseCardComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LtiCourseCardComponent;
    let fixture: ComponentFixture<LtiCourseCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LtiCourseCardComponent, MockDirective(TranslateDirective)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({}),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LtiCourseCardComponent);
        fixture.componentRef.setInput('course', { id: 1, shortName: 'lti-course', title: 'LTI COURSE' });
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should set default color if no color is specified in course', () => {
        fixture.detectChanges();
        expect(component.courseColor).toBe(ARTEMIS_DEFAULT_COLOR);
    });

    it('should use course color if specified', () => {
        const testColor = '#123456';
        fixture.componentRef.setInput('course', { id: 1, shortName: 'lti-course', title: 'LTI COURSE', color: testColor });
        fixture.detectChanges();
        expect(component.courseColor).toBe(testColor);
    });

    it('should display course ID', () => {
        const testCourse = {
            id: 123,
            shortName: 'test-course',
            title: 'Test Course',
            numberOfStudents: 25,
            startDate: dayjs('2023-01-01'),
            endDate: dayjs('2023-12-31'),
            description: 'Test description',
        };

        fixture.componentRef.setInput('course', testCourse);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        expect(compiled.querySelector('.card-body').textContent).toContain('ID: 123');
    });

    it('should display number of students', () => {
        const testCourse = {
            id: 1,
            shortName: 'test',
            title: 'Test',
            numberOfStudents: 42,
        };

        fixture.componentRef.setInput('course', testCourse);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        expect(compiled.querySelector('.card-body').textContent).toContain('42');
    });

    it('should display description when available', () => {
        const testDescription = 'This is a test course description';
        const testCourse = {
            id: 1,
            shortName: 'test',
            title: 'Test',
            description: testDescription,
        };

        fixture.componentRef.setInput('course', testCourse);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        expect(compiled.querySelector('.card-body').textContent).toContain(testDescription);
    });

    it('should compute contrasting text color for dark background', () => {
        const darkColor = '#000000';
        fixture.componentRef.setInput('course', { id: 1, shortName: 'test', title: 'Test', color: darkColor });
        fixture.detectChanges();

        expect(component.courseColor).toBe(darkColor);
        expect(component.contentColor).toBeDefined();
    });

    it('should compute contrasting text color for light background', () => {
        const lightColor = '#FFFFFF';
        fixture.componentRef.setInput('course', { id: 1, shortName: 'test', title: 'Test', color: lightColor });
        fixture.detectChanges();

        expect(component.courseColor).toBe(lightColor);
        expect(component.contentColor).toBeDefined();
    });

    it('should display course title', () => {
        const testCourse = {
            id: 1,
            shortName: 'test-short',
            title: 'My Test Course Title',
        };

        fixture.componentRef.setInput('course', testCourse);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        expect(compiled.querySelector('.card-header').textContent).toContain('My Test Course Title');
    });

    it('should handle course with dates', () => {
        const testCourse = {
            id: 1,
            shortName: 'test',
            title: 'Test',
            startDate: dayjs('2024-01-01'),
            endDate: dayjs('2024-12-31'),
        };

        fixture.componentRef.setInput('course', testCourse);
        fixture.detectChanges();

        expect(component.course()).toEqual(testCourse);
    });
});
