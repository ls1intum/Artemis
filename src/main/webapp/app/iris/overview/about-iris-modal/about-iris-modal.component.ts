import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { faBook, faBrain, faCompass, faLightbulb, faShieldHalved, faThumbsUp, faUser, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisChatControllerService } from 'app/iris/overview/services/iris-chat-controller.service';
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
    // Support both PrimeNG DynamicDialog and CDK MatDialog as overlay transports.
    // When opened from the exercise/lecture chat widget (MatDialog), the CDK ref is used;
    // when opened from the sidebar chat, the PrimeNG ref is used.
    private readonly dynamicDialogRef = inject(DynamicDialogRef, { optional: true });
    private readonly matDialogRef = inject(MatDialogRef, { optional: true });
    private readonly dialogConfig = inject(DynamicDialogConfig, { optional: true });
    private readonly accountService = inject(AccountService);

    // Resolve the host's controller via dual transport: CDK MatDialog propagates DI through
    // the host's viewContainerRef, while PrimeNG DialogService cannot — so the chat host
    // additionally passes the controller via `data: { controller }`. Outside a chat host
    // (control-center, onboarding) neither transport supplies one, so the "try Iris" button
    // is structurally unreachable.
    private readonly injectedController = inject(IrisChatControllerService, { optional: true });
    private readonly dialogControllerData = this.dialogConfig?.data?.controller as IrisChatControllerService | undefined;
    protected readonly controller: IrisChatControllerService | undefined = this.injectedController ?? this.dialogControllerData;

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
        this.dynamicDialogRef?.close();
        this.matDialogRef?.close();
    }

    tryIris(): void {
        if (!this.controller) {
            // Defensive: the template gates the button on `controller`, but guard the method too
            // so an accidental wire-up cannot trigger a no-op chat reset against a missing controller.
            return;
        }
        this.controller.clearChat();
        this.dynamicDialogRef?.close();
        this.matDialogRef?.close();
    }
}
