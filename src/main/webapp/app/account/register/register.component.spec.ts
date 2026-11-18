/**
 * Vitest tests for RegisterComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { ACCOUNT_REGISTRATION_BLOCKED, EMAIL_ALREADY_USED_TYPE, LOGIN_ALREADY_USED_TYPE } from 'app/foundation/constants/error.constants';
import { RegisterService } from 'app/account/register/register.service';
import { RegisterComponent } from 'app/account/register/register.component';
import { User } from 'app/account/user/user.model';
import { ElementRef } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { EMAIL_ALLOWED_DEBOUNCE_MS, EMAIL_ALLOWED_URL } from 'app/account/shared/email-allowed';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

describe('Register Component Tests', () => {
    describe('RegisterComponent', () => {
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
                    provideHttpClientTesting(),
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
            comp.registerForm.patchValue({ firstName: 'Test', lastName: 'User', login: 'testuser', email: 'test@example.org' });
        });

        describe('email restriction', () => {
            let http: HttpTestingController;

            function createWith(profileInfo: object): RegisterComponent {
                vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ registrationEnabled: true, ...profileInfo } as any);
                return TestBed.createComponent(RegisterComponent).componentInstance;
            }

            beforeEach(() => {
                vi.useFakeTimers();
                http = TestBed.inject(HttpTestingController);
            });

            afterEach(() => {
                vi.useRealTimers();
            });

            it('should ask the server after typing has paused and show its answer, whatever the pattern looks like', async () => {
                // a pattern that JavaScript could not even compile: only the server can decide, and it does
                const component = createWith({ allowedEmailPattern: '(?i)[\\p{L}.]+@example\\.com' });
                const email = component.registerForm.controls.email;

                email.setValue('ada@exam');
                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS / 2);
                email.setValue('ada@other.org');
                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);

                const request = http.expectOne(EMAIL_ALLOWED_URL);
                expect(request.request.body).toEqual({ email: 'ada@other.org' });
                request.flush({ allowed: false });
                expect(email.errors?.['emailNotAllowed']).toBeTruthy();
                expect(component.registerForm.invalid).toBe(true);

                email.setValue('Ada@EXAMPLE.com');
                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);
                http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: true });
                expect(email.valid).toBe(true);
            });

            it('should not hold the user up when the server cannot answer, because it decides again when the form is sent', async () => {
                const component = createWith({ allowedEmailPattern: '.*@example\\.com' });
                const email = component.registerForm.controls.email;

                email.setValue('ada@example.com');
                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);
                http.expectOne(EMAIL_ALLOWED_URL).flush('Too many requests', { status: 429, statusText: 'Too Many Requests' });

                expect(email.valid).toBe(true);
            });

            it('should leave the judgement of the address to the server with a restriction, also for an address that the browser would not accept', async () => {
                // the server accepts non-ASCII addresses, which the format check of Angular refuses
                const component = createWith({ allowedEmailPattern: '.*@example\\.com' });
                const email = component.registerForm.controls.email;

                email.setValue('Jörg.Müller@example.com');
                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);
                http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: true });

                expect(email.valid).toBe(true);
            });

            it('should not ask the server about an address that is too short to be one', async () => {
                const component = createWith({ allowedEmailPattern: '.*@example\\.com' });

                component.registerForm.controls.email.setValue('a@b');
                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);

                http.expectNone(EMAIL_ALLOWED_URL);
                expect(component.registerForm.controls.email.errors?.['minlength']).toBeTruthy();
            });

            it('should not send the form while the answer about the address is on its way', async () => {
                const component = createWith({ allowedEmailPattern: '.*@example\\.com' });
                const registerSpy = vi.spyOn(TestBed.inject(RegisterService), 'registerUser').mockReturnValue(of(undefined));
                component.registerForm.patchValue({
                    firstName: 'Ada',
                    lastName: 'Lovelace',
                    login: 'adal',
                    password: 'Str0ng!Passw0rd#2026',
                    confirmPassword: 'Str0ng!Passw0rd#2026',
                });

                component.registerForm.controls.email.setValue('ada@example.com');
                expect(component.registerForm.pending).toBe(true);
                component.register();
                expect(registerSpy).not.toHaveBeenCalled();

                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);
                http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: true });
                component.register();

                expect(registerSpy).toHaveBeenCalledOnce();
            });

            it('should check the format only, and never ask the server, without a restriction', async () => {
                const component = createWith({});
                const email = component.registerForm.controls.email;

                email.setValue('ada@example.org');
                await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);

                http.expectNone(EMAIL_ALLOWED_URL);
                expect(email.valid).toBe(true);
                email.setValue('not-an-address');
                expect(email.errors?.['email']).toBeTruthy();
            });
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
            vi.spyOn(registerService, 'registerUser').mockReturnValue(of({} as any));
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'password',
            });

            comp.register();

            const expectedUser = new User();
            expectedUser.email = 'test@example.org';
            expectedUser.firstName = 'Test';
            expectedUser.lastName = 'User';
            expectedUser.password = 'password';
            expectedUser.login = 'testuser';
            expectedUser.langKey = 'en';
            expect(registerService.registerUser).toHaveBeenCalledWith(expectedUser);
            expect(comp.success()).toBe(true);
            expect(comp.errorUserExists()).toBe(false);
            expect(comp.errorEmailExists()).toBe(false);
            expect(comp.error()).toBe(false);
        });

        it('should notify of user existence upon 400/login already in use', () => {
            vi.spyOn(registerService, 'registerUser').mockReturnValue(
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
            vi.spyOn(registerService, 'registerUser').mockReturnValue(
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

        it('should tell the user that the email address is not allowed instead of a generic failure when the server refuses it', () => {
            vi.spyOn(registerService, 'registerUser').mockReturnValue(
                throwError(() => ({
                    status: 400,
                    error: { errorKey: 'emailInvalid' },
                })),
            );
            comp.registerForm.patchValue({
                password: 'password',
                confirmPassword: 'password',
            });

            comp.register();

            expect(comp.errorEmailNotAllowed()).toBe(true);
            expect(comp.errorEmailExists()).toBe(false);
            expect(comp.error()).toBe(false);

            // the message disappears with the next attempt
            vi.spyOn(registerService, 'registerUser').mockReturnValue(of(undefined));
            comp.register();
            expect(comp.errorEmailNotAllowed()).toBe(false);
        });

        it('should notify of generic error', () => {
            vi.spyOn(registerService, 'registerUser').mockReturnValue(
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
            vi.spyOn(registerService, 'registerUser').mockReturnValue(
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

        it.each(['ä'.repeat(37), '€'.repeat(25), '😀'.repeat(19), 'ä'.repeat(36) + 'a'])('should reject passwords over 72 UTF-8 bytes: %s', (password) => {
            const registerSpy = vi.spyOn(registerService, 'registerUser').mockReturnValue(of(undefined));
            comp.registerForm.patchValue({ firstName: 'Test', lastName: 'User', login: 'testuser', email: 'test@example.org', password, confirmPassword: password });

            expect(comp.registerForm.controls.password.hasError('maxbytes')).toBe(true);
            comp.register();

            expect(registerSpy).not.toHaveBeenCalled();
            expect(comp.success()).toBe(false);
        });

        it.each(['ä'.repeat(36), '€'.repeat(24), '😀'.repeat(18)])('should register with a password at the 72-byte limit: %s', (password) => {
            vi.spyOn(registerService, 'registerUser').mockReturnValue(of(undefined));
            comp.registerForm.patchValue({ firstName: 'Test', lastName: 'User', login: 'testuser', email: 'test@example.org', password, confirmPassword: password });

            expect(comp.registerForm.valid).toBe(true);
            comp.register();

            expect(comp.success()).toBe(true);
            expect(registerService.registerUser).toHaveBeenCalledWith(expect.objectContaining({ password }));
        });

        it('should focus login input if login is defined', () => {
            // Create a mock element and spy on focus
            const mockElement = document.createElement('input');
            const focusSpy = vi.spyOn(mockElement, 'focus');

            // Mock the viewChild signal to return the element
            vi.spyOn(comp, 'loginInput').mockReturnValue({ nativeElement: mockElement } as ElementRef);

            comp.ngAfterViewInit();

            expect(focusSpy).toHaveBeenCalled();
        });
    });

    /**
     * The behaviour suite above renders no template at all, and the page itself is only reachable on a server
     * with registration enabled — so nothing else covers the markup of this form.
     */
    describe('RegisterComponent template', () => {
        let fixture: ComponentFixture<RegisterComponent>;
        let comp: RegisterComponent;

        const fields = ['firstName', 'lastName', 'login', 'email', 'password', 'confirmPassword'];

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [RegisterComponent],
                providers: [
                    FormBuilder,
                    LocalStorageService,
                    SessionStorageService,
                    ProfileService,
                    { provide: TranslateService, useClass: MockTranslateService },
                    provideHttpClient(),
                ],
            }).compileComponents();

            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ registrationEnabled: true } as any);

            fixture = TestBed.createComponent(RegisterComponent);
            comp = fixture.componentInstance;
            fixture.detectChanges();
        });

        const input = (id: string) => fixture.nativeElement.querySelector(`input#${id}`) as HTMLInputElement;

        it('renders every field as a labelled, required control', () => {
            for (const id of fields) {
                const control = input(id);
                expect(control, id).not.toBeNull();
                expect(control.required, id).toBe(true);

                const label = fixture.nativeElement.querySelector(`label[for="${id}"]`) as HTMLLabelElement;
                expect(label, id).not.toBeNull();
                expect(label.textContent!.trim(), id).not.toBe('');
            }
        });

        it('keeps the submit button disabled while the form is invalid', () => {
            expect(comp.registerForm.invalid).toBe(true);
            expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
        });

        it('explains the byte limit and allows submission after shortening the password', () => {
            comp.registerForm.setValue({
                firstName: 'Test',
                lastName: 'User',
                login: 'testuser',
                email: 'test@example.org',
                password: 'ä'.repeat(37),
                confirmPassword: 'ä'.repeat(37),
            });
            comp.registerForm.markAllAsTouched();
            fixture.detectChanges();

            const password = input('password');
            const error = fixture.nativeElement.querySelector(`#${password.getAttribute('aria-describedby')}`) as HTMLElement;
            expect(error.hidden).toBe(false);
            expect(error.textContent).toContain('global.messages.validate.newpassword.maxbytes');
            expect(password.getAttribute('aria-invalid')).toBe('true');
            expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);

            comp.registerForm.patchValue({ password: 'ä'.repeat(36), confirmPassword: 'ä'.repeat(36) });
            fixture.detectChanges();

            expect(password.getAttribute('aria-invalid')).toBeNull();
            expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(false);
        });

        it('announces a validation error and describes the control by it', () => {
            const login = input('login');
            comp.registerForm.controls.login.markAsTouched();
            fixture.detectChanges();

            const errorId = login.getAttribute('aria-describedby');
            expect(errorId).toBeTruthy();

            const error = fixture.nativeElement.querySelector(`#${errorId}`) as HTMLElement;
            expect(error.getAttribute('role')).toBe('alert');
            expect(error.hidden).toBe(false);
            expect(error.textContent!.trim()).not.toBe('');
            expect(login.getAttribute('aria-invalid')).toBe('true');
        });
    });
});
