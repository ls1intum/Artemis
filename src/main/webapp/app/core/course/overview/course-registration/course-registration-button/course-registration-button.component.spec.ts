import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { MockProvider } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseRegistrationButtonComponent } from 'app/core/course/overview/course-registration/course-registration-button/course-registration-button.component';
import { AlertService } from 'app/shared/service/alert.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('CourseRegistrationButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseRegistrationButtonComponent>;
    let component: CourseRegistrationButtonComponent;
    let courseService: CourseManagementService;
    let accountService: AccountService;
    let profileService: ProfileService;
    let registerForCourseStub: ReturnType<typeof vi.spyOn>;
    let identityStub: ReturnType<typeof vi.spyOn>;
    let getProfileInfoStub: ReturnType<typeof vi.spyOn>;
    let onRegistrationSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [MockProvider(AccountService), MockProvider(CourseManagementService), MockProvider(ProfileService), MockProvider(AlertService)],
        }).overrideTemplate(CourseRegistrationButtonComponent, '');
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseRegistrationButtonComponent);
        component = fixture.componentInstance;
        courseService = TestBed.inject(CourseManagementService);
        accountService = TestBed.inject(AccountService);
        profileService = TestBed.inject(ProfileService);
        onRegistrationSpy = vi.spyOn(component.onRegistration, 'emit');

        registerForCourseStub = vi.spyOn(courseService, 'registerForCourse').mockReturnValue(of(new HttpResponse({ body: ['student-group-name'] })));
        identityStub = vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve({ login: 'ga12tes' } as User));
        getProfileInfoStub = vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ allowedCourseRegistrationUsernamePattern: '^([a-z]{2}\\d{2}[a-z]{3})' } as ProfileInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(identityStub).toHaveBeenCalled();
        expect(getProfileInfoStub).toHaveBeenCalled();
    });

    it('should register for course', () => {
        component.registerForCourse(1);

        expect(registerForCourseStub).toHaveBeenCalledOnce();
    });

    it('should should fire onRegistration after registration', () => {
        component.registerForCourse(1);

        expect(onRegistrationSpy).toHaveBeenCalledOnce();
    });
});
