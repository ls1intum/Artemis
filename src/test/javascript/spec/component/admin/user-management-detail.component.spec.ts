import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

import { UserManagementDetailComponent } from 'app/admin/user-management/user-management-detail.component';
import { User } from 'app/core/user/user.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('User Management Detail Component', () => {
    let comp: UserManagementDetailComponent;
    let fixture: ComponentFixture<UserManagementDetailComponent>;
    const route = {
        data: of({ user: new User(1, 'user', 'first', 'last', 'first@last.com', true, 'en', [Authority.USER], ['admin']) }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserManagementDetailComponent);
                comp = fixture.componentInstance;
            });
    });

    describe('onInit', () => {
        it('should call load all on init', () => {
            // GIVEN

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(comp.user).toEqual(
                expect.objectContaining({
                    id: 1,
                    login: 'user',
                    firstName: 'first',
                    lastName: 'last',
                    email: 'first@last.com',
                    activated: true,
                    langKey: 'en',
                    authorities: [Authority.USER],
                    groups: ['admin'],
                    guidedTourSettings: [],
                }),
            );
        });
    });

    it('should show the user authorities and groups', () => {
        comp.ngOnInit();
        fixture.detectChanges();

        const roles = fixture.debugElement.queryAll(By.css('.bg-info'));
        expect(roles.map((role) => role.nativeElement.textContent)).toEqual(['ROLE_USER', 'admin']);
    });
});
