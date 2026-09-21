import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { IrisSearchAvailabilityService } from './iris-search-availability.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

describe('IrisSearchAvailabilityService', () => {
    // moduleEnabled is read once at construction, so providers must be configured before injecting the service.
    function createService(moduleEnabled: boolean, selectedLLMUsage?: LLMSelectionDecision): IrisSearchAvailabilityService {
        TestBed.configureTestingModule({
            providers: [
                IrisSearchAvailabilityService,
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(moduleEnabled) } },
                { provide: AccountService, useValue: { userIdentity: signal({ selectedLLMUsage }) } },
            ],
        });
        return TestBed.inject(IrisSearchAvailabilityService);
    }

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('returns false when the Iris module is disabled', () => {
        const service = createService(false, LLMSelectionDecision.CLOUD_AI);
        expect(service.contentSearchAvailable()).toBe(false);
    });

    it('returns false when the module is enabled but the user made no AI decision', () => {
        const service = createService(true, undefined);
        expect(service.contentSearchAvailable()).toBe(false);
    });

    it('returns true when the module is enabled and the user opted into local AI', () => {
        const service = createService(true, LLMSelectionDecision.LOCAL_AI);
        expect(service.contentSearchAvailable()).toBe(true);
    });

    it('returns true when the module is enabled and the user opted into cloud AI', () => {
        const service = createService(true, LLMSelectionDecision.CLOUD_AI);
        expect(service.contentSearchAvailable()).toBe(true);
    });
});
