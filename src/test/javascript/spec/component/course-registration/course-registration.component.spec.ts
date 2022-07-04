import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
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
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-prerequisites-modal.component';

describe('CourseRegistrationComponent', () => {
    let fixture: ComponentFixture<CourseRegistrationComponent>;
    let component: CourseRegistrationComponent;
    let courseService: CourseManagementService;
    let accountService: AccountService;
    let profileService: ProfileService;
    let modalService: NgbModal;
    let findAllToRegisterStub: jest.SpyInstance;
    let registerForCourseStub: jest.SpyInstance;
    let identityStub: jest.SpyInstance;
    let getProfileInfoStub: jest.SpyInstance;

    const course1 = {
        id: 1,
        title: 'Course A',
    } as Course;

    const course2 = {
        id: 2,
        title: 'Course B',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseRegistrationComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NgbModal, useClass: MockNgbModalService },
                MockProvider(TranslateService),
            ],
        })
            .overrideTemplate(CourseRegistrationComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseRegistrationComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                accountService = TestBed.inject(AccountService);
                profileService = TestBed.inject(ProfileService);
                modalService = TestBed.inject(NgbModal);

                findAllToRegisterStub = jest.spyOn(courseService, 'findAllToRegister').mockReturnValue(of(new HttpResponse({ body: [course1] })));
                registerForCourseStub = jest.spyOn(courseService, 'registerForCourse').mockReturnValue(of(new HttpResponse({ body: new User() })));
                identityStub = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve({ login: 'ga12tes' } as User));
                getProfileInfoStub = jest
                    .spyOn(profileService, 'getProfileInfo')
                    .mockReturnValue(of({ allowedCourseRegistrationUsernamePattern: '^([a-z]{2}\\d{2}[a-z]{3})' } as ProfileInfo));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(identityStub).toHaveBeenCalledOnce();
        expect(getProfileInfoStub).toHaveBeenCalledOnce();
        expect(component.userIsAllowedToRegister).toBeTrue();
    }));

    it('should show registrable courses', () => {
        component.loadRegistrableCourses();

        expect(component.coursesToSelect).toHaveLength(1);
        expect(findAllToRegisterStub).toHaveBeenCalledOnce();
    });

    it('should register for course', () => {
        component.coursesToSelect = [course1, course2];
        component.registerForCourse(1);

        expect(registerForCourseStub).toHaveBeenCalledOnce();
        expect(component.coursesToSelect).toEqual([course2]);
    });

    it('should open modal with prerequisites for course', () => {
        component.coursesToSelect = [course1];
        const openModalSpy = jest.spyOn(modalService, 'open');

        component.showPrerequisites(course1.id!);

        expect(openModalSpy).toHaveBeenCalledOnce();
        expect(openModalSpy).toHaveBeenCalledWith(CoursePrerequisitesModalComponent, { size: 'xl' });
    });
});
