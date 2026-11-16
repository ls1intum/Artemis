/**
 * Vitest tests of the rendered registration form. The other spec replaces the template, so what the person sees is checked here: the form is OnPush and the
 * application runs without zone.js, so a result that arrives after the form was last drawn (the answer of the server about the email address) must still update it.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { RegisterComponent } from 'app/account/register/register.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { EMAIL_ALLOWED_DEBOUNCE_MS, EMAIL_ALLOWED_URL } from 'app/account/shared/email-allowed';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('RegisterComponent template', () => {
    let fixture: ComponentFixture<RegisterComponent>;
    let http: HttpTestingController;

    const emailInput = () => fixture.nativeElement.querySelector('input#email') as HTMLInputElement;
    const submitButton = () => fixture.nativeElement.querySelector('tumaet-ui-button[type="submit"], button[type="submit"]') as HTMLElement;
    const text = () => fixture.nativeElement.textContent as string;

    async function typeEmail(value: string): Promise<void> {
        const input = emailInput();
        input.value = value;
        input.dispatchEvent(new Event('input'));
        input.dispatchEvent(new Event('blur'));
        await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);
    }

    /** Lets the change detection that Angular schedules itself run. The tests never call detectChanges after an answer, so a view that nobody marked stays stale. */
    async function letAngularRedraw(): Promise<void> {
        await vi.advanceTimersByTimeAsync(100);
    }

    async function create(profileInfo: object): Promise<void> {
        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ registrationEnabled: true, ...profileInfo } as any);
        fixture = TestBed.createComponent(RegisterComponent);
        http = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
        await letAngularRedraw();
    }

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            imports: [RegisterComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        });
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('should show that the address is not allowed as soon as the server answers, without any other interaction with the form', async () => {
        await create({ allowedEmailPattern: '.*@example\\.com' });

        await typeEmail('ada@other.org');
        // the question has been asked and the form is waiting for the answer
        expect(text()).not.toContain('global.messages.validate.email.notAllowed');
        http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: false });
        // no detectChanges here on purpose: the page has to redraw by itself
        await letAngularRedraw();

        expect(text()).toContain('global.messages.validate.email.notAllowed');
    });

    it('should enable the registration again as soon as the server allows the address', async () => {
        await create({ allowedEmailPattern: '.*@example\\.com' });
        fixture.componentInstance.registerForm.patchValue({
            firstName: 'Ada',
            lastName: 'Lovelace',
            login: 'adal',
            password: 'Str0ng!Passw0rd#2026',
            confirmPassword: 'Str0ng!Passw0rd#2026',
        });

        await typeEmail('ada@other.org');
        http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: false });
        await letAngularRedraw();
        expect(submitButton().querySelector('button')?.disabled ?? (submitButton() as HTMLButtonElement).disabled).toBe(true);

        await typeEmail('ada@example.com');
        http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: true });
        await letAngularRedraw();

        expect(text()).not.toContain('global.messages.validate.email.notAllowed');
        expect(submitButton().querySelector('button')?.disabled ?? (submitButton() as HTMLButtonElement).disabled).toBe(false);
    });
});
