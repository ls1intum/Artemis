import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DeleteUsersButtonComponent } from 'app/admin/user-management/delete-users-button.component';
import { AdminUserService } from 'app/core/user/admin-user.service';
import { ArtemisTestModule } from '../../test.module';
import { DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';

describe('DeleteUsersButtonComponent', () => {
    let comp: DeleteUsersButtonComponent;
    let fixture: ComponentFixture<DeleteUsersButtonComponent>;
    let adminUserService: AdminUserService;
    let deleteDialogService: DeleteDialogService;

    const dummyUserLogins: string[] = ['student42', 'tutor73'];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(DeleteUsersButtonComponent);
                comp = fixture.componentInstance;
                adminUserService = TestBed.inject(AdminUserService);
                deleteDialogService = TestBed.inject(DeleteDialogService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('loadUserList', () => {
        it('List of users', fakeAsync(() => {
            // GIVEN
            jest.spyOn(comp, 'openDeleteDialog').mockImplementation(/*Show now dialog*/);
            jest.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(
                of(
                    new HttpResponse({
                        body: dummyUserLogins,
                    }),
                ),
            );

            // WHEN
            comp.loadUserList();

            // THEN
            expect(adminUserService.queryNotEnrolledUsers).toHaveBeenCalledOnce();
            expect(comp.openDeleteDialog).toHaveBeenCalledOnce();
            expect(comp.users()).toEqual(dummyUserLogins);
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

    describe('openDeleteDialog', () => {
        it('calls method', fakeAsync(() => {
            // GIVEN
            jest.spyOn(deleteDialogService, 'openDeleteDialog').mockImplementation();

            // WHEN
            comp.openDeleteDialog();

            // THEN
            expect(deleteDialogService.openDeleteDialog).toHaveBeenCalledOnce();
        }));
    });

    describe('onConfirm', () => {
        it('Users to delete', fakeAsync(() => {
            // GIVEN
            jest.spyOn(adminUserService, 'deleteUsers').mockReturnValue(of(new HttpResponse<void>()));
            jest.spyOn(comp.deletionCompleted, 'emit');
            comp.users.set(dummyUserLogins);

            //WHEN
            comp.onConfirm();

            // THEN
            expect(adminUserService.deleteUsers).toHaveBeenCalledWith(dummyUserLogins);
            expect(comp.deletionCompleted.emit).toHaveBeenCalledOnce();
        }));

        it('Error response', fakeAsync(() => {
            // GIVEN
            jest.spyOn(adminUserService, 'deleteUsers').mockReturnValue(throwError(() => new Error('Some server side error ...')));
            jest.spyOn(comp.deletionCompleted, 'emit');
            comp.users.set(dummyUserLogins);

            //WHEN
            comp.onConfirm();

            // THEN
            expect(adminUserService.deleteUsers).toHaveBeenCalledWith(dummyUserLogins);
            expect(comp.deletionCompleted.emit).toHaveBeenCalledTimes(0);
        }));

        it('Empty users list', fakeAsync(() => {
            // GIVEN
            jest.spyOn(adminUserService, 'deleteUsers').mockImplementation();
            jest.spyOn(comp.deletionCompleted, 'emit');

            //WHEN
            comp.onConfirm();

            // THEN
            expect(adminUserService.deleteUsers).toHaveBeenCalledTimes(0);
            expect(comp.deletionCompleted.emit).toHaveBeenCalledTimes(0);
        }));
    });
});
