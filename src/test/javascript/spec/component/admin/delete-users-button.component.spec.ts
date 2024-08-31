import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DeleteUsersButtonComponent } from 'app/admin/user-management/delete-users-button.component';
import { AdminUserService } from 'app/core/user/admin-user.service';
import { ArtemisTestModule } from '../../test.module';

describe('DeleteUsersButtonComponent', () => {
    let comp: DeleteUsersButtonComponent;
    let fixture: ComponentFixture<DeleteUsersButtonComponent>;
    let adminUserService: AdminUserService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DeleteUsersButtonComponent);
                comp = fixture.componentInstance;
                adminUserService = TestBed.inject(AdminUserService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('loadUserList', () => {
        it('List of users', fakeAsync(() => {
            // GIVEN
            jest.spyOn(comp, 'openDeleteDialog').mockImplementation(/*Show now dialog*/);
            const users: string[] = ['student42', 'tutor73'];
            jest.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(
                of(
                    new HttpResponse({
                        body: users,
                    }),
                ),
            );

            // WHEN
            comp.loadUserList();

            // THEN
            expect(adminUserService.queryNotEnrolledUsers).toHaveBeenCalledOnce();
            expect(comp.openDeleteDialog).toHaveBeenCalledOnce();
            expect(comp.users()).toEqual(users);
        }));

        it('Nothing to delete message', fakeAsync(() => {
            // GIVEN
            jest.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [],
                    }),
                ),
            );

            // WHEN
            comp.loadUserList();

            // THEN
            expect(adminUserService.queryNotEnrolledUsers).toHaveBeenCalledOnce();
            expect(comp.users()).toBeEmpty();
            // TODO How to check if the / a message was shown?
        }));

        it('Error response', fakeAsync(() => {
            // GIVEN
            jest.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(throwError(() => new Error('Some server side error ...')));

            // WHEN
            comp.loadUserList();

            // THEN
            expect(adminUserService.queryNotEnrolledUsers).toHaveBeenCalledOnce();
            expect(comp.users()).toBeUndefined();
            // TODO How to check if the / a message was shown?
        }));
    });
});
