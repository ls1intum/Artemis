import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { LocalStorageService } from 'app/shared/storage/local-storage.service';
import { SessionStorageService } from 'app/shared/storage/session-storage.service';
import { of, throwError } from 'rxjs';

import { PasswordComponent } from 'app/core/account/password/password.component';
import { PasswordService } from 'app/core/account/password/password.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Password Component Tests', () => {
    describe('PasswordComponent', () => {
        let comp: PasswordComponent;
        let fixture: ComponentFixture<PasswordComponent>;
        let service: PasswordService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [PasswordComponent],
                providers: [
                    FormBuilder,
                    LocalStorageService,
                    SessionStorageService,
                    { provide: AccountService, useClass: MockAccountService },
                    { provide: ProfileService, useClass: MockProfileService },
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
            }).compileComponents();
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(PasswordComponent);
            comp = fixture.componentInstance;
            service = TestBed.inject(PasswordService);
            comp.passwordResetEnabled = true;
        });

        it('should show error if passwords do not match', () => {
            comp.ngOnInit();
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

            comp.ngOnInit();
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
            comp.ngOnInit();
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
            comp.ngOnInit();
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
            const expectedUser = { id: 99, login: 'admin' } as User;
            expect(comp.user).toEqual(expectedUser);
        }));
    });
});
