import { async, ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

import { ArtemisTestModule } from '../../../test.module';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockSyncStorage } from '../../../mocks/mock-sync.storage';

describe('Component Tests', () => {
    describe('User Management Component', () => {
        let comp: UserManagementComponent;
        let fixture: ComponentFixture<UserManagementComponent>;
        let service: UserService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [UserManagementComponent],
                providers: [AccountService, { provide: SessionStorageService, useClass: MockSyncStorage }, { provide: LocalStorageService, useClass: MockSyncStorage }],
            })
                .overrideTemplate(UserManagementComponent, '')
                .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(UserManagementComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(UserService);
        });

        describe('setActive', () => {
            it('Should update user and call load all', inject(
                [],
                fakeAsync(() => {
                    // GIVEN
                    const headers = new HttpHeaders().append('link', 'link;link');
                    const user = new User(123);
                    spyOn(service, 'query').and.returnValue(
                        of(
                            new HttpResponse({
                                body: [user],
                                headers,
                            }),
                        ),
                    );
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ status: 200 })));

                    // WHEN
                    comp.setActive(user, true);
                    tick(2000); // simulate async (needs to wait for the debounce time in UserManagementComponent)

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(user);
                    expect(service.query).toHaveBeenCalled();
                    expect(comp.users[0]).toEqual(jasmine.objectContaining({ id: 123 }));
                }),
            ));
        });
    });
});
