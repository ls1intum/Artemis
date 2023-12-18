import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
import { Course } from 'app/entities/course.model';
import { ArtemisTestModule } from '../../test.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CoursePrerequisitesButtonComponent } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';
import { CourseRegistrationButtonComponent } from 'app/overview/course-registration/course-registration-button/course-registration-button.component';
import { AccountService } from 'app/core/auth/account.service';

describe('CourseRegistrationComponent', () => {
    let fixture: ComponentFixture<CourseRegistrationComponent>;
    let component: CourseRegistrationComponent;
    let courseService: CourseManagementService;
    let findAllForRegistrationStub: jest.SpyInstance;

    const course1 = {
        id: 1,
        title: 'Course A',
        semester: 'SS25/26',
    } as Course;

    const course2 = {
        id: 2,
        title: 'Course B',
        semester: 'WS22/23',
    };
    const course3 = {
        id: 3,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseRegistrationComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CoursePrerequisitesButtonComponent),
                MockComponent(CourseRegistrationButtonComponent),
            ],
            providers: [MockProvider(AccountService), MockProvider(CourseManagementService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseRegistrationComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);

                findAllForRegistrationStub = jest.spyOn(courseService, 'findAllForRegistration').mockReturnValue(of(new HttpResponse({ body: [course1] })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show registrable courses', () => {
        component.loadRegistrableCourses();
        expect(component.coursesToSelect).toHaveLength(1);
        expect(findAllForRegistrationStub).toHaveBeenCalledOnce();
    });

    it('should be able to remove courses from its list', () => {
        component.loadRegistrableCourses();
        component.removeCourseFromList(course1.id!);

        expect(component.coursesToSelect).toHaveLength(0);
    });

    it('should filter registrable courses based on search term', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course2, course3] })));

        component.loadRegistrableCourses();
        component.searchTermString = 'Course A';
        component.applySearch();

        expect(component.filteredCoursesToSelect).toHaveLength(1);
        expect(component.filteredCoursesToSelect[0]).toEqual(course1);
    });

    it('should sort registrable courses by title in ascending order', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course3, course2, course1] })));

        component.predicate = 'title';
        component.ascending = true;
        component.loadRegistrableCourses();

        expect(component.filteredCoursesToSelect).toEqual([course1, course2, course3]);
    });

    it('should sort registrable courses by title in descending order', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course1, course3, course2] })));

        component.predicate = 'title';
        component.ascending = false;
        component.loadRegistrableCourses();

        expect(component.filteredCoursesToSelect).toEqual([course3, course2, course1]);
    });

    it('should sort registrable courses by semester in ascending order', () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course2, course3, course1] })));
        component.predicate = 'semester';
        component.ascending = true;
        component.loadRegistrableCourses();

        expect(component.filteredCoursesToSelect).toEqual([course1, course2, course3]);
    });

    it('should sort registrable courses by semester in descending order', async () => {
        findAllForRegistrationStub.mockReturnValue(of(new HttpResponse({ body: [course2, course3, course1] })));
        component.predicate = 'semester';
        component.ascending = false;
        component.loadRegistrableCourses();

        expect(component.filteredCoursesToSelect).toEqual([course3, course2, course1]);
    });
});
