import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HeaderCourseComponent } from 'app/core/course/manage/header-course/header-course.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ComponentRef } from '@angular/core';

describe('Header Course Component', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<HeaderCourseComponent>;
    let component: HeaderCourseComponent;
    let componentRef: ComponentRef<HeaderCourseComponent>;

    const courseWithLongDescription: Course = {
        id: 123,
        title: 'Course Title1',
        shortName: 'ShortName1',
        description: 'a'.repeat(1000),
    };

    const courseWithShortDescription: Course = {
        id: 234,
        title: 'Course Title2',
        shortName: 'ShortName2',
        description: 'a'.repeat(100),
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(HeaderCourseComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        window['innerWidth'] = 1920;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should toggle long course description', () => {
        componentRef.setInput('course', courseWithLongDescription);
        fixture.detectChanges();

        expect(component.enableShowMore()).toBe(true);
        expect(component.longDescriptionShown()).toBe(false);
        expect(component.courseDescription()).toBe('a'.repeat(564) + '...');

        component.toggleCourseDescription();

        expect(component.longDescriptionShown()).toBe(true);
        expect(component.courseDescription()).toBe(courseWithLongDescription.description);

        component.toggleCourseDescription();

        expect(component.longDescriptionShown()).toBe(false);
        expect(component.courseDescription()).toBe('a'.repeat(564) + '...');
    });

    it('should not enable show more for course with short description', () => {
        componentRef.setInput('course', courseWithShortDescription);
        fixture.detectChanges();

        expect(component.enableShowMore()).toBe(false);
        expect(component.longDescriptionShown()).toBe(false);
        expect(component.courseDescription()).toBe(courseWithShortDescription.description);

        window['innerWidth'] = 100;
        component.onResize();
        expect(component.enableShowMore()).toBe(true);
        expect(component.courseDescription()).toBe('a'.repeat(29) + '...');

        component.toggleCourseDescription();
        expect(component.enableShowMore()).toBe(true);
        expect(component.longDescriptionShown()).toBe(true);
        expect(component.courseDescription()).toBe(courseWithShortDescription.description);

        window['innerWidth'] = 1920;
        component.onResize();
        expect(component.enableShowMore()).toBe(false);
        expect(component.courseDescription()).toBe(courseWithShortDescription.description);
    });

    it('should not display manage button but go to student view button in course management', () => {
        const courseWithTutor = { ...courseWithShortDescription, isAtLeastTutor: true };
        componentRef.setInput('course', courseWithTutor);

        fixture.changeDetectorRef.detectChanges();

        const manageButton = fixture.nativeElement.querySelector('#manage-button');
        expect(manageButton).toBeNull();

        const showStudentViewButton = fixture.nativeElement.querySelector('#student-view-button');
        // when the TranslateDirective is missing in the component, the textContent is an empty string
        expect(showStudentViewButton.textContent).toBe('artemisApp.courseOverview.studentView');
        expect(showStudentViewButton).toBeTruthy();
    });

    it('should not display manage button to student', () => {
        const courseWithStudent = { ...courseWithShortDescription, isAtLeastTutor: false };
        componentRef.setInput('course', courseWithStudent);

        fixture.changeDetectorRef.detectChanges();

        const manageButton = fixture.nativeElement.querySelector('#manage-button');
        expect(manageButton).toBeNull();
    });
});
