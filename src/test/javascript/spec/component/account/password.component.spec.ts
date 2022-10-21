import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { PasswordComponent } from 'app/account/password/password.component';
import { PasswordService } from 'app/account/password/password.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';

import { ArtemisTestModule } from '../../test.module';

describe('Component Tests', () => {
    describe('PasswordComponent', () => {
        let comp: PasswordComponent;
        let fixture: ComponentFixture<PasswordComponent>;
        let service: PasswordService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [PasswordComponent],
                providers: [
                    FormBuilder,
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: AccountService, useClass: MockAccountService },
                    { provide: ProfileService, useClass: MockProfileService },
                ],
            }).compileComponents();
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(PasswordComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(PasswordService);
            comp.passwordResetEnabled = true;
        });

        it('should show error if passwords do not match', () => {
            // GIVEN
            comp.passwordForm.patchValue({
                newPassword: 'password1',
                confirmPassword: 'password2',
            });
            // WHEN
            comp.changePassword();
            // THEN
            expect(comp.doNotMatch).toBeTrue();
            expect(comp.error).toBeFalse();
            expect(comp.success).toBeFalse();
        });

        it('should call Auth.changePassword when passwords match', () => {
            // GIVEN
            const passwordValues = {
                currentPassword: 'oldPassword',
                newPassword: 'myPassword',
            };

            jest.spyOn(service, 'save').mockReturnValue(of(new HttpResponse({ body: true })));

            comp.passwordForm.patchValue({
                currentPassword: passwordValues.currentPassword,
                newPassword: passwordValues.newPassword,
                confirmPassword: passwordValues.newPassword,
            });

            // WHEN
            comp.changePassword();

            // THEN
            expect(service.save).toHaveBeenCalledWith(passwordValues.newPassword, passwordValues.currentPassword);
        });

        it('should set success to true upon success', () => {
            // GIVEN
            jest.spyOn(service, 'save').mockReturnValue(of(new HttpResponse({ body: true })));
            comp.passwordForm.patchValue({
                newPassword: 'myPassword',
                confirmPassword: 'myPassword',
            });

            // WHEN
            comp.changePassword();

            // THEN
            expect(comp.doNotMatch).toBeFalse();
            expect(comp.error).toBeFalse();
            expect(comp.success).toBeTrue();
        });

        it('should notify of error if change password fails', () => {
            // GIVEN
            jest.spyOn(service, 'save').mockReturnValue(throwError(() => new Error('ERROR')));
            comp.passwordForm.patchValue({
                newPassword: 'myPassword',
                confirmPassword: 'myPassword',
            });

            // WHEN
            comp.changePassword();

            // THEN
            expect(comp.doNotMatch).toBeFalse();
            expect(comp.success).toBeFalse();
            expect(comp.error).toBeTrue();
        });

        it('sets user on init', fakeAsync(() => {
            fixture.detectChanges();
            tick(1000);
            const expectedUser = { id: 99 } as User;
            expect(comp.user).toEqual(expectedUser);
        }));
    });
});
