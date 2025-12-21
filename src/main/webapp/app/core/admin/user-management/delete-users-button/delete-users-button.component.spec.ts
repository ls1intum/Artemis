/**
 * Vitest tests for DeleteUsersButtonComponent.
 * Tests the component that handles bulk deletion of non-enrolled users.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';

import { DeleteUsersButtonComponent } from 'app/core/admin/user-management/delete-users-button/delete-users-button.component';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { AlertService } from 'app/shared/service/alert.service';
import * as globalUtils from 'app/shared/util/global.utils';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DeleteUsersButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: DeleteUsersButtonComponent;
    let fixture: ComponentFixture<DeleteUsersButtonComponent>;
    let adminUserService: AdminUserService;
    let alertService: AlertService;
    let deleteDialogService: DeleteDialogService;

    /** Sample user logins to be deleted */
    const testUserLogins: string[] = ['student42', 'tutor73'];
    /** Sample error for testing error handling */
    const testError: Error = new Error('Some server side error ...');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [DeleteUsersButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        })
            .overrideTemplate(DeleteUsersButtonComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(DeleteUsersButtonComponent);
        component = fixture.componentInstance;
        adminUserService = TestBed.inject(AdminUserService);
        alertService = TestBed.inject(AlertService);
        deleteDialogService = TestBed.inject(DeleteDialogService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('loadUserList', () => {
        it('should load users and open delete dialog when users exist', () => {
            vi.spyOn(component, 'openDeleteDialog').mockImplementation(() => {});
            vi.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(
                of(
                    new HttpResponse({
                        body: testUserLogins,
                    }),
                ),
            );

            component.loadUserList();

            expect(adminUserService.queryNotEnrolledUsers).toHaveBeenCalledOnce();
            expect(component.openDeleteDialog).toHaveBeenCalledOnce();
            expect(component.users()).toEqual(testUserLogins);
        });

        it('should show info message when no users found to delete', () => {
            vi.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [],
                    }),
                ),
            );
            vi.spyOn(alertService, 'info');

            component.loadUserList();

            expect(adminUserService.queryNotEnrolledUsers).toHaveBeenCalledOnce();
            expect(component.users()).toHaveLength(0);
            expect(alertService.info).toHaveBeenCalledWith('artemisApp.userManagement.notEnrolled.delete.cancel');
        });

        it('should handle error response and call onError', () => {
            vi.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(throwError(() => testError));
            vi.spyOn(globalUtils, 'onError');

            component.loadUserList();

            expect(adminUserService.queryNotEnrolledUsers).toHaveBeenCalledOnce();
            expect(component.users()).toBeUndefined();
            expect(globalUtils.onError).toHaveBeenCalledWith(alertService, testError);
        });
    });

    describe('openDeleteDialog', () => {
        it('should call delete dialog service', () => {
            vi.spyOn(deleteDialogService, 'openDeleteDialog').mockImplementation(() => {});

            component.openDeleteDialog();

            expect(deleteDialogService.openDeleteDialog).toHaveBeenCalledOnce();
        });
    });

    describe('onConfirm', () => {
        it('should delete users and emit completion event on success', () => {
            vi.spyOn(adminUserService, 'deleteUsers').mockReturnValue(of(new HttpResponse<void>()));
            vi.spyOn(component.deletionCompleted, 'emit');
            component.users.set(testUserLogins);

            component.onConfirm();

            expect(adminUserService.deleteUsers).toHaveBeenCalledWith(testUserLogins);
            expect(component.deletionCompleted.emit).toHaveBeenCalledOnce();
        });

        it('should not emit completion event when deletion fails', () => {
            vi.spyOn(adminUserService, 'deleteUsers').mockReturnValue(throwError(() => testError));
            vi.spyOn(component.deletionCompleted, 'emit');
            component.users.set(testUserLogins);

            component.onConfirm();

            expect(adminUserService.deleteUsers).toHaveBeenCalledWith(testUserLogins);
            expect(component.deletionCompleted.emit).not.toHaveBeenCalled();
        });

        it('should not call delete service when users list is empty', () => {
            vi.spyOn(adminUserService, 'deleteUsers').mockImplementation(() => of(new HttpResponse<void>()));
            vi.spyOn(component.deletionCompleted, 'emit');

            component.onConfirm();

            expect(adminUserService.deleteUsers).not.toHaveBeenCalled();
            expect(component.deletionCompleted.emit).not.toHaveBeenCalled();
        });
    });
});
