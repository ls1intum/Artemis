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
    } as Course;

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

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should show registrable courses', () => {
        component.loadRegistrableCourses();

        expect(component.coursesToSelect).toHaveLength(1);
        expect(findAllForRegistrationStub).toHaveBeenCalledOnce();
    });
});
