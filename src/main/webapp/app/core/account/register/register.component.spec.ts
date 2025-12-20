/**
 * Vitest tests for RegisterComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormBuilder } from '@angular/forms';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { ACCOUNT_REGISTRATION_BLOCKED, EMAIL_ALREADY_USED_TYPE, LOGIN_ALREADY_USED_TYPE } from 'app/shared/constants/error.constants';
import { RegisterService } from 'app/core/account/register/register.service';
import { RegisterComponent } from 'app/core/account/register/register.component';
import { User } from 'app/core/user/user.model';
import { ElementRef } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';

describe('Register Component Tests', () => {
    describe('RegisterComponent', () => {
        setupTestBed({ zoneless: true });

        let fixture: ComponentFixture<RegisterComponent>;
        let comp: RegisterComponent;
        let translateService: TranslateService;
        let registerService: RegisterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [RegisterComponent],
                providers: [
                    FormBuilder,
                    LocalStorageService,
                    SessionStorageService,
                    ProfileService,
                    { provide: TranslateService, useClass: MockTranslateService },
                    provideHttpClient(),
                ],
            })
                .overrideTemplate(RegisterComponent, '')
                .compileComponents();
        });

        beforeEach(() => {
            // Mock ProfileService to return registrationEnabled: true before component creation
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ registrationEnabled: true } as any);

            fixture = TestBed.createComponent(RegisterComponent);
            translateService = TestBed.inject(TranslateService);
            registerService = TestBed.inject(RegisterService);
            comp = fixture.componentInstance;
            translateService.use('en');
        });

        it('should ensure the two passwords entered match', () => {
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'non-matching',
            });

            comp.register();

            expect(comp.doNotMatch()).toBe(true);
        });

        it('should update success to true after creating an account', () => {
            vi.spyOn(registerService, 'save').mockReturnValue(of({} as any));
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'password',
            });

            comp.register();

            const user = new User();
            user.email = '';
            user.firstName = '';
            user.lastName = '';
            user.password = 'password';
            user.login = '';
            user.langKey = 'en';
            expect(registerService.save).toHaveBeenCalledWith(user);
            expect(comp.success()).toBe(true);
            expect(comp.errorUserExists()).toBe(false);
            expect(comp.errorEmailExists()).toBe(false);
            expect(comp.error()).toBe(false);
        });

        it('should notify of user existence upon 400/login already in use', () => {
            vi.spyOn(registerService, 'save').mockReturnValue(
                throwError(() => ({
                    status: 400,
                    error: { type: LOGIN_ALREADY_USED_TYPE },
                })),
            );
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'password',
            });

            comp.register();

            expect(comp.errorUserExists()).toBe(true);
            expect(comp.errorEmailExists()).toBe(false);
            expect(comp.error()).toBe(false);
        });

        it('should notify of email existence upon 400/email address already in use', () => {
            vi.spyOn(registerService, 'save').mockReturnValue(
                throwError(() => ({
                    status: 400,
                    error: { type: EMAIL_ALREADY_USED_TYPE },
                })),
            );
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'password',
            });

            comp.register();

            expect(comp.errorEmailExists()).toBe(true);
            expect(comp.errorUserExists()).toBe(false);
            expect(comp.error()).toBe(false);
        });

        it('should notify of generic error', () => {
            vi.spyOn(registerService, 'save').mockReturnValue(
                throwError(() => ({
                    status: 503,
                })),
            );
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'password',
            });

            comp.register();

            expect(comp.errorUserExists()).toBe(false);
            expect(comp.errorEmailExists()).toBe(false);
            expect(comp.error()).toBe(true);
        });

        it('should notify of account registration blocked upon 400/account registration blocked', () => {
            vi.spyOn(registerService, 'save').mockReturnValue(
                throwError(() => ({
                    status: 400,
                    error: { type: ACCOUNT_REGISTRATION_BLOCKED },
                })),
            );
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'password',
            });

            comp.register();

            expect(comp.errorAccountRegistrationBlocked()).toBe(true);
            expect(comp.errorUserExists()).toBe(false);
            expect(comp.errorEmailExists()).toBe(false);
            expect(comp.error()).toBe(false);
        });

        it('should focus login input if login is defined', () => {
            // Create a mock element and spy on focus
            const mockElement = document.createElement('input');
            const focusSpy = vi.spyOn(mockElement, 'focus');

            // Mock the viewChild signal to return the element
            vi.spyOn(comp, 'login').mockReturnValue({ nativeElement: mockElement } as ElementRef);

            comp.ngAfterViewInit();

            expect(focusSpy).toHaveBeenCalled();
        });
    });
});
