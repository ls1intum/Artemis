import { ChangeDetectionStrategy, Component, computed, inject, input, model } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { faBook, faBrain, faCompass, faLightbulb, faShieldHalved, faThumbsUp, faUser, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { TumAetUiButtonDirective, TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

interface FeatureCard {
    titleKey: string;
    descKey: string;
    icon: IconDefinition;
}

@Component({
    selector: 'jhi-about-iris-modal',
    templateUrl: './about-iris-modal.component.html',
    styleUrl: './about-iris-modal.component.scss',
    imports: [IrisLogoComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiDialogComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AboutIrisModalComponent {
    private readonly chatService = inject(IrisChatService);
    private readonly accountService = inject(AccountService);

    /** Two-way visibility, driven by the host: the Iris chat and the course settings pages. */
    readonly visible = model(false);
    /** Hides the "Try Iris" action on pages that have no chat to start a session in. */
    readonly hideTryButton = input(false);

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly faXmark = faXmark;
    protected readonly faShield = faShieldHalved;

    readonly privacyDescKey = computed(() => {
        const decision = this.accountService.userIdentity()?.selectedLLMUsage;
        if (decision === LLMSelectionDecision.LOCAL_AI) {
            return 'artemisApp.iris.aboutIrisModal.privacyDescLocal';
        }
        return 'artemisApp.iris.aboutIrisModal.privacyDesc';
    });

    protected readonly whatIrisCanDo: FeatureCard[] = [
        {
            titleKey: 'artemisApp.iris.aboutIrisModal.contextAwareTitle',
            descKey: 'artemisApp.iris.aboutIrisModal.contextAwareDesc',
            icon: faBrain,
        },
        {
            titleKey: 'artemisApp.iris.aboutIrisModal.guidedLearningTitle',
            descKey: 'artemisApp.iris.aboutIrisModal.guidedLearningDesc',
            icon: faLightbulb,
        },
        {
            titleKey: 'artemisApp.iris.aboutIrisModal.feedbackHelpsTitle',
            descKey: 'artemisApp.iris.aboutIrisModal.feedbackHelpsDesc',
            icon: faThumbsUp,
        },
    ];

    protected readonly whatToExpect: FeatureCard[] = [
        {
            titleKey: 'artemisApp.iris.aboutIrisModal.guideNotSolveTitle',
            descKey: 'artemisApp.iris.aboutIrisModal.guideNotSolveDesc',
            icon: faCompass,
        },
        {
            titleKey: 'artemisApp.iris.aboutIrisModal.stayOnTopicTitle',
            descKey: 'artemisApp.iris.aboutIrisModal.stayOnTopicDesc',
            icon: faBook,
        },
        {
            titleKey: 'artemisApp.iris.aboutIrisModal.ownYourWorkTitle',
            descKey: 'artemisApp.iris.aboutIrisModal.ownYourWorkDesc',
            icon: faUser,
        },
    ];

    close(): void {
        this.visible.set(false);
    }

    tryIris(): void {
        this.chatService.startFreshChat();
        this.close();
    }
}
