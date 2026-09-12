import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { AiExperienceOptInService } from 'app/logos/ai-experience-opt-in.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { UserService } from 'app/account/user/shared/user.service';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

describe('AiExperienceOptInService', () => {
    let service: AiExperienceOptInService;
    let accountService: AccountService;
    let userService: UserService;
    let llmModalService: LLMSelectionModalService;

    const mockUserService = {
        updateLLMSelectionDecision: vi.fn().mockReturnValue(of(new HttpResponse<void>())),
    } as any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                AiExperienceOptInService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserService, useValue: mockUserService },
                LLMSelectionModalService,
            ],
        });

        service = TestBed.inject(AiExperienceOptInService);
        accountService = TestBed.inject(AccountService);
        userService = TestBed.inject(UserService);
        llmModalService = TestBed.inject(LLMSelectionModalService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        mockUserService.updateLLMSelectionDecision.mockClear();
    });

    describe('hasAcceptedAiUsage', () => {
        it.each([LLMSelectionDecision.CLOUD_AI, LLMSelectionDecision.LOCAL_AI])('returns true for %s', (selection) => {
            accountService.userIdentity.set({ selectedLLMUsage: selection } as any);
            expect(service.hasAcceptedAiUsage()).toBe(true);
        });

        it('returns false for NO_AI', () => {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as any);
            expect(service.hasAcceptedAiUsage()).toBe(false);
        });

        it('returns false when the user has no decision yet', () => {
            accountService.userIdentity.set(undefined);
            expect(service.hasAcceptedAiUsage()).toBe(false);
        });
    });

    describe('promptForAiUsage', () => {
        it('does not persist a decision or call onAccepted when the modal is dismissed', async () => {
            vi.spyOn(llmModalService, 'open').mockResolvedValue(LLM_MODAL_DISMISSED);
            const onAccepted = vi.fn();

            service.promptForAiUsage(onAccepted);
            await vi.waitFor(() => expect(llmModalService.open).toHaveBeenCalled());

            expect(userService.updateLLMSelectionDecision).not.toHaveBeenCalled();
            expect(onAccepted).not.toHaveBeenCalled();
        });

        it('persists CLOUD_AI and calls onAccepted', async () => {
            vi.spyOn(llmModalService, 'open').mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
            vi.spyOn(accountService, 'setUserLLMSelectionDecision');
            const onAccepted = vi.fn();

            service.promptForAiUsage(onAccepted);
            await vi.waitFor(() => expect(onAccepted).toHaveBeenCalled());

            expect(userService.updateLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
            expect(accountService.setUserLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('persists NO_AI but does not call onAccepted', async () => {
            vi.spyOn(llmModalService, 'open').mockResolvedValue(LLMSelectionDecision.NO_AI);
            vi.spyOn(accountService, 'setUserLLMSelectionDecision');
            const onAccepted = vi.fn();

            service.promptForAiUsage(onAccepted);
            await vi.waitFor(() => expect(userService.updateLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI));

            expect(accountService.setUserLLMSelectionDecision).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
            expect(onAccepted).not.toHaveBeenCalled();
        });
    });
});
