import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LlmUsageSettingsComponent } from './llm-usage-settings.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { MockDirective, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('LlmUsageSettingsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LlmUsageSettingsComponent;
    let fixture: ComponentFixture<LlmUsageSettingsComponent>;
    let irisChatService: IrisChatService;
    let accountService: AccountService;
    let llmModalService: LLMSelectionModalService;

    const mockUser: User = {
        id: 1,
        login: 'testuser',
        selectedLLMUsage: LLMSelectionDecision.CLOUD_AI,
        selectedLLMUsageTimestamp: dayjs('2025-01-01T10:00:00.000Z'),
    } as User;

    beforeEach(async () => {
        const irisChatServiceMock = {
            updateLLMUsageConsent: vi.fn(),
        };

        const accountServiceMock = {
            userIdentity: vi.fn().mockReturnValue(mockUser),
            setUserLLMSelectionDecision: vi.fn(),
        };

        const llmModalServiceMock = {
            open: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [LlmUsageSettingsComponent, MockDirective(TranslateDirective), MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: IrisChatService, useValue: irisChatServiceMock },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: LLMSelectionModalService, useValue: llmModalServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LlmUsageSettingsComponent);
        component = fixture.componentInstance;
        irisChatService = TestBed.inject(IrisChatService);
        accountService = TestBed.inject(AccountService);
        llmModalService = TestBed.inject(LLMSelectionModalService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('ngOnInit', () => {
        it('should call updateLLMUsageDecision on init', () => {
            const updateSpy = vi.spyOn(component as any, 'updateLLMUsageDecision');

            component.ngOnInit();

            expect(updateSpy).toHaveBeenCalledOnce();
        });

        it('should set current LLM selection decision from user identity', () => {
            component.ngOnInit();

            expect(component.currentLLMSelectionDecision()).toBe(LLMSelectionDecision.CLOUD_AI);
        });

        it('should set current LLM selection decision date from user identity', () => {
            component.ngOnInit();

            expect(component.currentLLMSelectionDecisionDate()).toEqual(dayjs('2025-01-01T10:00:00.000Z'));
        });

        it('should set undefined when user has no LLM selection decision', () => {
            (accountService.userIdentity as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
                selectedLLMUsage: undefined,
                selectedLLMUsageTimestamp: undefined,
            } as User);

            component.ngOnInit();

            expect(component.currentLLMSelectionDecision()).toBeUndefined();
            expect(component.currentLLMSelectionDecisionDate()).toBeUndefined();
        });
    });

    describe('openSelectionModal', () => {
        it('should handle CLOUD_AI choice', async () => {
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
            const updateSpy = vi.spyOn(component, 'updateLLMSelectionDecision');

            await component.openSelectionModal();

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(updateSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should handle LOCAL_AI choice', async () => {
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(LLMSelectionDecision.LOCAL_AI);
            const updateSpy = vi.spyOn(component, 'updateLLMSelectionDecision');

            await component.openSelectionModal();

            // Default mockUser has CLOUD_AI selected, so open receives CLOUD_AI
            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(updateSpy).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should handle NO_AI choice', async () => {
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(LLMSelectionDecision.NO_AI);
            const updateSpy = vi.spyOn(component, 'updateLLMSelectionDecision');

            await component.openSelectionModal();

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(updateSpy).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
        });

        it('should not update when choice is NONE', async () => {
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(LLM_MODAL_DISMISSED);
            const updateSpy = vi.spyOn(component, 'updateLLMSelectionDecision');

            await component.openSelectionModal();

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should not update when choice is null', async () => {
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(null);
            const updateSpy = vi.spyOn(component, 'updateLLMSelectionDecision');

            await component.openSelectionModal();

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should not update when choice is undefined', async () => {
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(undefined);
            const updateSpy = vi.spyOn(component, 'updateLLMSelectionDecision');

            await component.openSelectionModal();

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should pass current selection to modal when user has LOCAL_AI selected', async () => {
            (accountService.userIdentity as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
                ...mockUser,
                selectedLLMUsage: LLMSelectionDecision.LOCAL_AI,
            } as User);
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(LLM_MODAL_DISMISSED);

            await component.openSelectionModal();

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should pass current selection to modal when user has NO_AI selected', async () => {
            (accountService.userIdentity as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
                ...mockUser,
                selectedLLMUsage: LLMSelectionDecision.NO_AI,
            } as User);
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(LLM_MODAL_DISMISSED);

            await component.openSelectionModal();

            expect(llmModalService.open).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
        });

        it('should pass undefined to modal when user has no selection', async () => {
            (accountService.userIdentity as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
                ...mockUser,
                selectedLLMUsage: undefined,
            } as User);
            component.ngOnInit();
            (llmModalService.open as ReturnType<typeof vi.fn>).mockResolvedValue(LLM_MODAL_DISMISSED);

            await component.openSelectionModal();

            expect(llmModalService.open).toHaveBeenCalledWith(undefined);
        });
    });

    describe('updateLLMSelectionDecision', () => {
        it('should call irisChatService.updateLLMUsageConsent', () => {
            component.updateLLMSelectionDecision(LLMSelectionDecision.CLOUD_AI);

            expect(irisChatService.updateLLMUsageConsent).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should call accountService.setUserLLMSelectionDecision', () => {
            component.updateLLMSelectionDecision(LLMSelectionDecision.LOCAL_AI);

            expect(accountService.setUserLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should update internal state after updating decision', () => {
            const updateSpy = vi.spyOn(component as any, 'updateLLMUsageDecision');

            component.updateLLMSelectionDecision(LLMSelectionDecision.NO_AI);

            expect(updateSpy).toHaveBeenCalledOnce();
        });

        it('should handle CLOUD_AI decision', () => {
            component.updateLLMSelectionDecision(LLMSelectionDecision.CLOUD_AI);

            expect(irisChatService.updateLLMUsageConsent).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(accountService.setUserLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should handle LOCAL_AI decision', () => {
            component.updateLLMSelectionDecision(LLMSelectionDecision.LOCAL_AI);

            expect(irisChatService.updateLLMUsageConsent).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
            expect(accountService.setUserLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should handle NO_AI decision', () => {
            component.updateLLMSelectionDecision(LLMSelectionDecision.NO_AI);

            expect(irisChatService.updateLLMUsageConsent).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
            expect(accountService.setUserLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
        });
    });

    describe('LLMSelectionDecision constant', () => {
        it('should expose LLMSelectionDecision enum', () => {
            expect(component['LLMSelectionDecision']).toBe(LLMSelectionDecision);
        });
    });
});
