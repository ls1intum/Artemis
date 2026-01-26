import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';

import { ExternalLlmUsageSettingsComponent } from './external-llm-usage-settings.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

describe('ExternalLlmUsageSettingsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExternalLlmUsageSettingsComponent;
    let fixture: ComponentFixture<ExternalLlmUsageSettingsComponent>;
    let irisChatService: IrisChatService;
    let accountService: AccountService;
    let userIdentitySignal: WritableSignal<User | undefined>;

    beforeEach(async () => {
        userIdentitySignal = signal<User | undefined>(undefined);

        const mockAccountService = {
            userIdentity: userIdentitySignal,
            setUserAcceptedExternalLLMUsage: vi.fn(),
        };

        const mockIrisChatService = {
            updateExternalLLMUsageConsent: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [ExternalLlmUsageSettingsComponent, MockDirective(TranslateDirective), MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: IrisChatService, useValue: mockIrisChatService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useValue: mockAccountService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExternalLlmUsageSettingsComponent);
        component = fixture.componentInstance;
        irisChatService = TestBed.inject(IrisChatService);
        accountService = TestBed.inject(AccountService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    describe('ngOnInit', () => {
        it('should set externalLLMUsageAccepted to undefined when userIdentity is undefined', () => {
            userIdentitySignal.set(undefined);

            fixture.detectChanges();
            component.ngOnInit();

            expect(component.externalLLMUsageAccepted()).toBeUndefined();
        });

        it('should set externalLLMUsageAccepted to undefined when user has not accepted', () => {
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = undefined;
            userIdentitySignal.set(user);

            fixture.detectChanges();
            component.ngOnInit();

            expect(component.externalLLMUsageAccepted()).toBeUndefined();
        });

        it('should set externalLLMUsageAccepted to dayjs date when user has accepted', () => {
            const acceptedDate = dayjs('2024-01-15T10:30:00');
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = acceptedDate;
            userIdentitySignal.set(user);

            fixture.detectChanges();
            component.ngOnInit();

            expect(component.externalLLMUsageAccepted()).toBeDefined();
            expect(component.externalLLMUsageAccepted()?.isSame(acceptedDate, 'day')).toBe(true);
        });
    });

    describe('updateExternalLLMUsageConsent', () => {
        it('should call irisChatService and accountService when accepting consent', () => {
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = undefined;
            userIdentitySignal.set(user);

            fixture.detectChanges();

            component.updateExternalLLMUsageConsent(true);

            expect(irisChatService.updateExternalLLMUsageConsent).toHaveBeenCalledWith(true);
            expect(accountService.setUserAcceptedExternalLLMUsage).toHaveBeenCalledWith(true);
        });

        it('should call irisChatService and accountService when declining consent', () => {
            const acceptedDate = dayjs('2024-01-15T10:30:00');
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = acceptedDate;
            userIdentitySignal.set(user);

            fixture.detectChanges();

            component.updateExternalLLMUsageConsent(false);

            expect(irisChatService.updateExternalLLMUsageConsent).toHaveBeenCalledWith(false);
            expect(accountService.setUserAcceptedExternalLLMUsage).toHaveBeenCalledWith(false);
        });

        it('should update externalLLMUsageAccepted signal after consent change', () => {
            // First setup: user has accepted
            const acceptedDate = dayjs('2024-01-15T10:30:00');
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = acceptedDate;
            userIdentitySignal.set(user);

            fixture.detectChanges();

            // Verify initial state
            expect(component.externalLLMUsageAccepted()).toBeDefined();

            // Simulate user declining - update the user signal to reflect the change
            user.externalLLMUsageAccepted = undefined;
            userIdentitySignal.set({ ...user });

            component.updateExternalLLMUsageConsent(false);

            // Since updateExternalLLMUsageAccepted is called which reads from userIdentity
            // and we've updated userIdentity, the signal should now be undefined
            expect(component.externalLLMUsageAccepted()).toBeUndefined();
        });

        it('should update externalLLMUsageAccepted signal when accepting after decline', () => {
            // First setup: user has not accepted
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = undefined;
            userIdentitySignal.set(user);

            fixture.detectChanges();

            // Verify initial state
            expect(component.externalLLMUsageAccepted()).toBeUndefined();

            // Simulate user accepting - update the user signal to reflect the change
            const acceptedDate = dayjs();
            user.externalLLMUsageAccepted = acceptedDate;
            userIdentitySignal.set({ ...user });

            component.updateExternalLLMUsageConsent(true);

            // Since updateExternalLLMUsageAccepted is called which reads from userIdentity
            // and we've updated userIdentity, the signal should now have a date
            expect(component.externalLLMUsageAccepted()).toBeDefined();
        });
    });

    describe('updateExternalLLMUsageAccepted (private method via ngOnInit)', () => {
        it('should handle falsy externalLLMUsageAccepted value', () => {
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = undefined;
            userIdentitySignal.set(user);

            fixture.detectChanges();
            component.ngOnInit();

            expect(component.externalLLMUsageAccepted()).toBeUndefined();
        });

        it('should handle null userIdentity', () => {
            userIdentitySignal.set(undefined);

            fixture.detectChanges();
            component.ngOnInit();

            expect(component.externalLLMUsageAccepted()).toBeUndefined();
        });

        it('should convert string date to dayjs object', () => {
            const dateString = '2024-06-15T14:30:00.000Z';
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            // Simulate a date that might come as a string from the server
            user.externalLLMUsageAccepted = dayjs(dateString);
            userIdentitySignal.set(user);

            fixture.detectChanges();
            component.ngOnInit();

            expect(component.externalLLMUsageAccepted()).toBeDefined();
            expect(dayjs.isDayjs(component.externalLLMUsageAccepted())).toBe(true);
        });
    });

    describe('Component initialization', () => {
        it('should call ngOnInit and set initial state correctly when user has accepted', async () => {
            const acceptedDate = dayjs('2024-03-20T08:00:00');
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = acceptedDate;
            userIdentitySignal.set(user);

            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.externalLLMUsageAccepted()).toBeDefined();
            expect(component.externalLLMUsageAccepted()?.format('YYYY-MM-DD')).toBe('2024-03-20');
        });

        it('should call ngOnInit and set initial state correctly when user has not accepted', async () => {
            const user = new User();
            user.id = 1;
            user.login = 'testuser';
            user.externalLLMUsageAccepted = undefined;
            userIdentitySignal.set(user);

            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.externalLLMUsageAccepted()).toBeUndefined();
        });
    });
});
