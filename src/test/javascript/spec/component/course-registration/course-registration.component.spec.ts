import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
import { Course } from 'app/entities/course.model';
import { ArtemisTestModule } from '../../test.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';

describe('CourseRegistrationComponent', () => {
    let fixture: ComponentFixture<CourseRegistrationComponent>;
    let component: CourseRegistrationComponent;
    let courseService: CourseManagementService;
    let findAllToRegisterStub: jest.SpyInstance;
    let registerForCourseStub: jest.SpyInstance;

    const course1 = {
        id: 1,
    } as Course;
    
    const course2 = {
        id: 2,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseRegistrationComponent],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .overrideTemplate(CourseRegistrationComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseRegistrationComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                
                findAllToRegisterStub = jest.spyOn(courseService, 'findAllToRegister').mockReturnValue(of(new HttpResponse({ body: [course1] })));
                registerForCourseStub = jest.spyOn(courseService, 'registerForCourse').mockReturnValue(of(new HttpResponse({ body: new User() })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should track course by Id', () => {
        const indexNumber = 1;
        expect(component.trackCourseById(indexNumber, course1)).toEqual(indexNumber);
    });

    it('should show registratable courses', fakeAsync(() => {
        component.courses = [course2];

        component.loadAndFilterCourses();
        tick();

        expect(component.coursesToSelect.length).toEqual(1);
        expect(findAllToRegisterStub).toHaveBeenCalledTimes(1);
    }));

    it('should register for course', fakeAsync(() => {
        component.registerForCourse(1);
        tick();

        expect(component.addedSuccessful).toBe(true);
        expect(registerForCourseStub).toHaveBeenCalledTimes(1);
        flush();
    }));
});
