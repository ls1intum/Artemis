import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { of, Subscription } from 'rxjs';
import { AbstractControl, ReactiveFormsModule } from '@angular/forms';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ArtemisTestModule } from '../../test.module';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { EventManager } from 'app/core/util/event-manager.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('UserManagementComponent', () => {
    let comp: UserManagementComponent;
    let fixture: ComponentFixture<UserManagementComponent>;
    let userService: UserService;
    let accountService: AccountService;
    let eventManager: EventManager;
    let httpMock: HttpTestingController;

    const route = {
        params: of({ courseId: 123, sort: 'id,desc' }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(ReactiveFormsModule), MockModule(NgbModule), HttpClientTestingModule],
            declarations: [
                UserManagementComponent,
                MockRouterLinkDirective,
                MockComponent(ItemCountComponent),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockDirective(SortDirective),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: of({
                            defaultSort: 'name,asc',
                        }),
                        queryParamMap: of(
                            new Map([
                                ['page', '1'],
                                ['sort', 'id,asc'],
                            ]),
                        ),
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserManagementComponent);
                comp = fixture.componentInstance;
                userService = TestBed.inject(UserService);
                accountService = TestBed.inject(AccountService);
                eventManager = TestBed.inject(EventManager);
                httpMock = TestBed.inject(HttpTestingController);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        httpMock.verify();
    });

    it('should parse the user search result into the correct component state', fakeAsync(() => {
        const headers = new HttpHeaders().append('link', 'link;link').append('X-Total-Count', '1');
        jest.spyOn(userService, 'query').mockReturnValue(
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

        expect(comp.users).toHaveLength(1);
        expect(comp.users[0].id).toBe(1);
        expect(comp.totalItems).toBe(1);
        expect(comp.loadingSearchResult).toBeFalse();
    }));

    describe('setActive', () => {
        it('Should update user and call load all', inject(
            [],
            fakeAsync(() => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                const user = new User(123);
                jest.spyOn(userService, 'query').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [user],
                            headers,
                        }),
                    ),
                );
                jest.spyOn(userService, 'update').mockReturnValue(of(new HttpResponse<User>({ status: 200 })));

                // WHEN
                comp.setActive(user, true);
                tick(1000); // simulate async

                // THEN
                expect(userService.update).toHaveBeenCalledWith({ ...user, activated: true });
                expect(userService.query).toHaveBeenCalledOnce();
                expect(comp.users && comp.users[0]).toEqual(expect.objectContaining({ id: 123 }));
            }),
        ));
    });

    it('should searchForm + currentUser and handle navigation on init', fakeAsync(() => {
        const identitySpy = jest.spyOn(accountService, 'identity');
        const user = new User(123);
        const querySpy = jest.spyOn(userService, 'query').mockReturnValue(
            of(
                new HttpResponse({
                    body: [user],
                }),
            ),
        );
        comp.ngOnInit();
        tick(1000);

        expect(identitySpy).toHaveBeenCalledOnce();
        expect(comp.currentAccount).toEqual({ id: 99 });

        expect(comp.page).toBe(1);
        expect(comp.predicate).toBe('id');
        expect(comp.ascending).toBeTrue();

        expect(querySpy).toHaveBeenCalledOnce();
    }));

    it('should destroy the user list subscription on destroy', () => {
        const object = {};
        comp.userListSubscription = object as Subscription;

        const destroySpy = jest.spyOn(eventManager, 'destroy').mockImplementation(jest.fn());
        comp.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
        expect(destroySpy).toHaveBeenCalledWith(object);
    });

    it('should return the user id or -1 from trackIdentity', () => {
        expect(comp.trackIdentity(0, { id: 1 } as User)).toBe(1);
        expect(comp.trackIdentity(0, { id: undefined } as User)).toBe(-1);
    });

    it.each([
        { status: 200, statusText: '' },
        { status: 400, statusText: 'Delete Failure' },
    ])('should broadcast after user deletion, or show error', ({ status, statusText }) => {
        const deleteSpy = jest.spyOn(userService, 'delete');
        const broadcastSpy = jest.spyOn(eventManager, 'broadcast');
        let errorText: string | undefined = undefined;
        comp.dialogError.subscribe((text) => (errorText = text));

        comp.deleteUser('test');
        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith('test');
        const reqD = httpMock.expectOne(SERVER_API_URL + 'api/users/test');
        reqD.flush(null, { status, statusText });

        if (status === 200) {
            expect(broadcastSpy).toHaveBeenCalledOnce();
            expect(broadcastSpy).toHaveBeenCalledWith({ name: 'userListModification', content: 'Deleted a user' });
        } else {
            expect(broadcastSpy).not.toHaveBeenCalled();
        }
        expect(errorText).toInclude(statusText);

        jest.restoreAllMocks();
    });

    it('should validate user search correctly', () => {
        expect(comp.validateUserSearch({ value: [] } as AbstractControl)).toBe(null);
        expect(comp.validateUserSearch({ value: [0] } as AbstractControl)).toEqual({ searchControl: true });
        expect(comp.validateUserSearch({ value: [0, 0] } as AbstractControl)).toEqual({ searchControl: true });
        expect(comp.validateUserSearch({ value: [0, 0, 0] } as AbstractControl)).toBe(null);
    });
});
