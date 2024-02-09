import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { ArtemisTestModule } from '../../test.module';
import { Course } from 'app/entities/course.model';
import { MockPipe, MockProvider } from 'ng-mocks';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, CommonModule, MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective],
            declarations: [HeaderCourseComponent],
            providers: [MockProvider(Router)],
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

    it('should display manage button', () => {
        component.course = courseWithShortDescription;
        component.course.isAtLeastTutor = true;
        const router = TestBed.inject(Router);
        const urlSpy = jest.spyOn(router, 'url', 'get');
        urlSpy.mockReturnValue('/some-url');

        fixture.detectChanges();

        const manageButton = fixture.nativeElement.querySelector('#manage-button');
        expect(manageButton).toBeTruthy();
    });

    it('should not display manage button but go to student view button in course management', () => {
        component.course = courseWithShortDescription;
        component.course!.isAtLeastTutor = true;
        const router = TestBed.inject(Router);
        const urlSpy = jest.spyOn(router, 'url', 'get');
        urlSpy.mockReturnValue('/course-management/some-path');

        fixture.detectChanges();

        const manageButton = fixture.nativeElement.querySelector('#manage-button');
        expect(manageButton).toBeNull();

        const showStudentViewButton = fixture.nativeElement.querySelector('#student-view-button');
        expect(showStudentViewButton).toBeTruthy();
    });

    it('should not display manage button to student', () => {
        component.course = courseWithShortDescription;
        component.course!.isAtLeastTutor = false;
        const router = TestBed.inject(Router);
        const urlSpy = jest.spyOn(router, 'url', 'get');
        urlSpy.mockReturnValue('/some-url');

        fixture.detectChanges();

        const manageButton = fixture.nativeElement.querySelector('#manage-button');
        expect(manageButton).toBeNull();
    });

    it('should not display student view button in student view', () => {
        component.course = courseWithShortDescription;
        component.course!.isAtLeastTutor = false;
        const router = TestBed.inject(Router);
        const urlSpy = jest.spyOn(router, 'url', 'get');
        urlSpy.mockReturnValue('/courses');

        const showManageLectureButton = fixture.nativeElement.querySelector('#student-view-button');
        expect(showManageLectureButton).toBeNull();
    });
});
