import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';

import { DeleteUsersButtonComponent } from 'app/admin/user-management/delete-users-button/delete-users-button.component';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { AlertService } from 'app/foundation/service/alert.service';
import * as globalUtils from 'app/foundation/util/global.utils';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('DeleteUsersButtonComponent', () => {
    let component: DeleteUsersButtonComponent;
    let fixture: ComponentFixture<DeleteUsersButtonComponent>;
    let adminUserService: AdminUserService;
    let alertService: AlertService;

    const testUserLogins = ['student42', 'tutor73'];
    const testError = new Error('Some server side error ...');

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
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render a button that loads deletion candidates', async () => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [DeleteUsersButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        const renderFixture = TestBed.createComponent(DeleteUsersButtonComponent);
        const loadUserListSpy = vi.spyOn(renderFixture.componentInstance, 'loadUserList').mockImplementation(() => {});
        renderFixture.detectChanges();

        const button = renderFixture.nativeElement.querySelector('[data-testid="delete-users-button"]');
        expect(button).toBeTruthy();
        (button.querySelector('button') ?? button).click();
        expect(loadUserListSpy).toHaveBeenCalledOnce();
    });

    it('should load candidates and delegate deletion to the parent impact dialog', () => {
        vi.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(of(new HttpResponse({ body: testUserLogins })));
        vi.spyOn(component.deletionRequested, 'emit');

        component.loadUserList();

        expect(component.users()).toEqual(testUserLogins);
        expect(component.deletionRequested.emit).toHaveBeenCalledWith(testUserLogins);
    });

    it('should show an information message when there are no candidates', () => {
        vi.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(of(new HttpResponse({ body: [] })));
        vi.spyOn(alertService, 'info');
        vi.spyOn(component.deletionRequested, 'emit');

        component.loadUserList();

        expect(alertService.info).toHaveBeenCalledWith('artemisApp.userManagement.notEnrolled.delete.cancel');
        expect(component.deletionRequested.emit).not.toHaveBeenCalled();
    });

    it('should forward loading errors to the alert utility', () => {
        vi.spyOn(adminUserService, 'queryNotEnrolledUsers').mockReturnValue(throwError(() => testError));
        vi.spyOn(globalUtils, 'onError');

        component.loadUserList();

        expect(globalUtils.onError).toHaveBeenCalledWith(alertService, testError);
    });

    it('should emit the currently loaded candidates', () => {
        vi.spyOn(component.deletionRequested, 'emit');
        component.users.set(testUserLogins);

        component.openDeleteDialog();

        expect(component.deletionRequested.emit).toHaveBeenCalledWith(testUserLogins);
    });
});
