/**
 * Vitest tests for UserManagementDetailComponent.
 * Tests the user detail view that displays user information from the route.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';

import { UserManagementDetailComponent } from 'app/admin/user-management/detail/user-management-detail.component';
import { User } from 'app/account/user/user.model';
import { Authority } from 'app/foundation/constants/authority.constants';
import { UserCourseRolesComponent } from 'app/admin/user-management/course-roles/user-course-roles.component';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

describe('UserManagementDetailComponent', () => {
    let component: UserManagementDetailComponent;
    let fixture: ComponentFixture<UserManagementDetailComponent>;

    /** Sample user data provided through the route resolver */
    const testUser = new User(1, 'user', 'first', 'last', 'first@last.com', true, 'en', [Authority.STUDENT]);

    /** Mock ActivatedRoute with user data in the route's data observable */
    const mockRoute = {
        data: of({ user: testUser }),
        children: [],
    } as unknown as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserManagementDetailComponent],
            providers: [{ provide: ActivatedRoute, useValue: mockRoute }],
        })
            .overrideTemplate(UserManagementDetailComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(UserManagementDetailComponent);
        component = fixture.componentInstance;
    });

    describe('ngOnInit', () => {
        it('should load user data from route on initialization', () => {
            component.ngOnInit();

            expect(component.user()).toEqual(
                expect.objectContaining({
                    id: 1,
                    login: 'user',
                    firstName: 'first',
                    lastName: 'last',
                    email: 'first@last.com',
                    activated: true,
                    langKey: 'en',
                    authorities: [Authority.STUDENT],
                }),
            );
        });
    });

    describe('course roles', () => {
        it('should embed the course roles of the displayed user with the real template, identified by the login', async () => {
            TestBed.resetTestingModule();
            await TestBed.configureTestingModule({
                imports: [UserManagementDetailComponent],
                providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService(), { provide: ActivatedRoute, useValue: mockRoute }],
            })
                // The picture and the title bar are not under test and need services of their own.
                .overrideComponent(UserManagementDetailComponent, { remove: { imports: [ProfilePictureComponent, AdminTitleBarTitleDirective] } })
                .compileComponents();
            const httpMock = TestBed.inject(HttpTestingController);
            const detail = TestBed.createComponent(UserManagementDetailComponent);
            detail.componentInstance.ngOnInit();
            detail.detectChanges();

            const roles = detail.debugElement.query(By.directive(UserCourseRolesComponent));
            expect(roles).not.toBeNull();
            expect(roles.componentInstance.login()).toBe('user');
            httpMock.expectOne({ method: 'GET', url: 'api/account/admin/users/user/course-roles' }).flush([]);
            httpMock.verify();
        });
    });
});
