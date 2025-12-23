import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HeaderCourseComponent } from 'app/core/course/manage/header-course/header-course.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('Header Course Component', () => {
    let fixture: ComponentFixture<HeaderCourseComponent>;
    let component: HeaderCourseComponent;

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
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(HeaderCourseComponent);
                component = fixture.componentInstance;
            });
        window['innerWidth'] = 1920;
    });

    it('should toggle long course description', () => {
        component.course = courseWithLongDescription;

        component.ngOnChanges();
        expect(component.enableShowMore).toBeTrue();
        expect(component.longDescriptionShown).toBeFalse();
        expect(component.courseDescription).toBe('a'.repeat(564) + '…');

        component.toggleCourseDescription();

        expect(component.longDescriptionShown).toBeTrue();
        expect(component.courseDescription).toBe(courseWithLongDescription.description);

        component.toggleCourseDescription();

        expect(component.longDescriptionShown).toBeFalse();
        expect(component.courseDescription).toBe('a'.repeat(564) + '…');
    });

    it('should not enable show more for course with short description', () => {
        component.course = courseWithShortDescription;

        component.ngOnChanges();
        expect(component.enableShowMore).toBeFalse();
        expect(component.longDescriptionShown).toBeFalse();
        expect(component.courseDescription).toBe(courseWithShortDescription.description);

        window['innerWidth'] = 100;
        component.onResize();
        expect(component.enableShowMore).toBeTrue();
        expect(component.courseDescription).toBe('a'.repeat(29) + '…');

        component.toggleCourseDescription();
        expect(component.enableShowMore).toBeTrue();
        expect(component.longDescriptionShown).toBeTrue();
        expect(component.courseDescription).toBe(courseWithShortDescription.description);

        window['innerWidth'] = 1920;
        component.onResize();
        expect(component.enableShowMore).toBeFalse();
        expect(component.courseDescription).toBe(courseWithShortDescription.description);
    });

    it('should not display manage button but go to student view button in course management', () => {
        component.course = courseWithShortDescription;
        component.course!.isAtLeastTutor = true;

        fixture.changeDetectorRef.detectChanges();

        const manageButton = fixture.nativeElement.querySelector('#manage-button');
        expect(manageButton).toBeNull();

        const showStudentViewButton = fixture.nativeElement.querySelector('#student-view-button');
        // when the TranslateDirective is missing in the component, the textContent is an empty string
        expect(showStudentViewButton.textContent).toBe('artemisApp.courseOverview.studentView');
        expect(showStudentViewButton).toBeTruthy();
    });

    it('should not display manage button to student', () => {
        component.course = courseWithShortDescription;
        component.course!.isAtLeastTutor = false;

        fixture.changeDetectorRef.detectChanges();

        const manageButton = fixture.nativeElement.querySelector('#manage-button');
        expect(manageButton).toBeNull();
    });
});
