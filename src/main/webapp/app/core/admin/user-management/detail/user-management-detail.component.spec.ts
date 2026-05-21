/**
 * Vitest tests for UserManagementDetailComponent.
 * Tests the user detail view that displays user information from the route.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';

import { UserManagementDetailComponent } from 'app/core/admin/user-management/detail/user-management-detail.component';
import { User } from 'app/core/user/user.model';
import { Authority } from 'app/shared/constants/authority.constants';

describe('UserManagementDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let component: UserManagementDetailComponent;
    let fixture: ComponentFixture<UserManagementDetailComponent>;

    /** Sample user data provided through the route resolver */
    const testUser = new User(1, 'user', 'first', 'last', 'first@last.com', true, 'en', [Authority.STUDENT], ['admin']);

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
                    groups: ['admin'],
                }),
            );
        });
    });
});
