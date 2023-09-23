import { TestBed } from '@angular/core/testing';
import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { ArtemisTestModule } from '../../test.module';
import { Course } from 'app/entities/course.model';
import { MockProvider } from 'ng-mocks';
import { Router } from '@angular/router';

describe('Header Course Component', () => {
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [HeaderCourseComponent, MockProvider(Router)],
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                component = TestBed.createComponent(HeaderCourseComponent).componentInstance;
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

    it('should display manage button', () => {
        component.course = courseWithShortDescription;
        component.course.isAtLeastTutor = true;
        const router = TestBed.inject(Router);
        const urlSpy = jest.spyOn(router, 'url', 'get');
        urlSpy.mockReturnValue('/some-url');

        const showManageLectureButton = component.shouldShowGoToCourseManagementButton();
        expect(showManageLectureButton).toBeTrue();
    });

    it('should not display manage button in course management', () => {
        component.course = courseWithShortDescription;
        component.course!.isAtLeastTutor = true;
        const router = TestBed.inject(Router);
        const urlSpy = jest.spyOn(router, 'url', 'get');
        urlSpy.mockReturnValue('/course-management/some-path');

        const showManageLectureButton = component.shouldShowGoToCourseManagementButton();
        expect(showManageLectureButton).toBeFalse();
    });

    it('should not display manage button to student', () => {
        component.course = courseWithShortDescription;
        component.course!.isAtLeastTutor = false;
        const router = TestBed.inject(Router);
        const urlSpy = jest.spyOn(router, 'url', 'get');
        urlSpy.mockReturnValue('/some-url');

        const showManageLectureButton = component.shouldShowGoToCourseManagementButton();
        expect(showManageLectureButton).toBeFalse();
    });

    it('should redirect to course management', () => {
        component.course = courseWithShortDescription;
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.redirectToCourseManagement();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 234]);
    });
});
