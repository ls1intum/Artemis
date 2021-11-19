import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ActivatedRoute } from '@angular/router';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { of } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockModule } from 'ng-mocks/cjs/lib/mock-module/mock-module';
import { MockComponent } from 'ng-mocks/cjs/lib/mock-component/mock-component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ReactiveFormsModule } from '@angular/forms';
import { Directive, Input } from '@angular/core';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { HttpClientTestingModule } from '@angular/common/http/testing';

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[routerLink]' })
export class MockRouterLinkDirective {
    @Input('routerLink') data: any;
}

describe('UserManagementComponent', () => {
    let comp: UserManagementComponent;
    let fixture: ComponentFixture<UserManagementComponent>;
    let service: UserService;

    const route = {
        params: of({ courseId: 123, sort: 'id,desc' }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(ReactiveFormsModule), MockModule(NgbModule), HttpClientTestingModule, MockModule(RouterTestingModule)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            declarations: [
                UserManagementComponent,
                MockComponent(AlertErrorComponent),
                MockComponent(AlertComponent),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockComponent(ItemCountComponent),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserManagementComponent);
                comp = fixture.componentInstance;
                service = fixture.debugElement.injector.get(UserService);
            });
    });

    // The admin module is lazy loaded - we therefore need a dummy test to load
    // the module and verify that there are no dependency related issues.
    it('should render a component from the admin module', () => {
        expect(comp).not.toBe(null);
    });

    it('should parse the user search result into the correct component state', inject(
        [],
        fakeAsync(() => {
            const headers = new HttpHeaders().append('link', 'link;link').append('X-Total-Count', '1');
            jest.spyOn(service, 'query').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [new User(1)],
                        headers,
                    }),
                ),
            );

            comp.loadAll();
            // 1 sec of pause, because of the debounce time
            tick(1000);

            expect(comp.users && comp.users[0].id).toEqual(1);
            expect(comp.totalItems).toEqual(1);
            expect(comp.loadingSearchResult).toEqual(false);
        }),
    ));

    describe('setActive', () => {
        it('Should update user and call load all', inject(
            [],
            fakeAsync(() => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                const user = new User(123);
                jest.spyOn(service, 'query').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [user],
                            headers,
                        }),
                    ),
                );
                jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse<User>({ status: 200 })));

                // WHEN
                comp.setActive(user, true);
                tick(1000); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith({ ...user, activated: true });
                expect(service.query).toHaveBeenCalledTimes(1);
                expect(comp.users && comp.users[0]).toEqual(expect.objectContaining({ id: 123 }));
            }),
        ));
    });
});
