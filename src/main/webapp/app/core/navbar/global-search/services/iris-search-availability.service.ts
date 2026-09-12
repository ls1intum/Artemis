import { Injectable, computed, inject } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

@Injectable({ providedIn: 'root' })
export class IrisSearchAvailabilityService {
    private readonly profileService = inject(ProfileService);
    private readonly accountService = inject(AccountService);

    // False when artemis.iris.enabled = false in the server config.
    private readonly moduleEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);

    // True only when the module is enabled AND the user opted into AI usage.
    readonly contentSearchAvailable = computed(() => {
        if (!this.moduleEnabled) {
            return false;
        }
        const usage = this.accountService.userIdentity()?.selectedLLMUsage;
        return usage === LLMSelectionDecision.LOCAL_AI || usage === LLMSelectionDecision.CLOUD_AI;
    });
}
