import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { faBook, faBrain, faCompass, faLightbulb, faShieldHalved, faThumbsUp, faUser, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { ButtonDirective } from 'primeng/button';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

interface FeatureCard {
    titleKey: string;
    descKey: string;
    icon: IconDefinition;
}

@Component({
    selector: 'jhi-about-iris-modal',
    templateUrl: './about-iris-modal.component.html',
    styleUrl: './about-iris-modal.component.scss',
    imports: [IrisLogoComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, ButtonDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AboutIrisModalComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly chatService = inject(IrisChatService);
    private readonly accountService = inject(AccountService);

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
        this.dialogRef.close();
    }

    tryIris(): void {
        this.chatService.clearChat();
        this.dialogRef.close();
    }
}
