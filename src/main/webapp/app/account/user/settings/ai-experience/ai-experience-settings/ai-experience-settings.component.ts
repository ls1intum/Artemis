import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGhost } from '@fortawesome/free-solid-svg-icons';
import { AiModeSelectorComponent } from 'app/logos/ai-mode-selector.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { TumAetUiButtonDirective, TumAetUiCardComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-ai-experience-settings',
    imports: [
        ArtemisTranslatePipe,
        FaIconComponent,
        TranslateDirective,
        DeleteButtonDirective,
        RouterLink,
        AiModeSelectorComponent,
        TumAetUiButtonDirective,
        TumAetUiCardComponent,
    ],
    templateUrl: './ai-experience-settings.component.html',
})
export class AiExperienceSettingsComponent implements OnInit {
    private readonly accountService = inject(AccountService);
    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly irisChatService = inject(IrisChatService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);

    protected readonly ActionType = ActionType;
    protected readonly faGhost = faGhost;

    readonly isSavingConsent = this.irisChatService.isSavingLLMUsageConsent;
    readonly currentSelection = computed(() => this.accountService.userIdentity()?.selectedLLMUsage);
    readonly isOnPremiseEnabled = this.profileService.isLLMDeploymentEnabled();
    sessionCount = signal(0);
    messageCount = signal(0);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.loadCounts();
    }

    selectMode(choice: LLMSelectionDecision): void {
        if (choice !== this.currentSelection()) {
            this.irisChatService.updateLLMUsageConsent(choice);
        }
    }

    deleteAllIrisInteractions() {
        this.irisChatHttpService.deleteAllSessions().subscribe({
            next: () => {
                this.sessionCount.set(0);
                this.messageCount.set(0);
                this.dialogErrorSource.next('');
                this.alertService.success('artemisApp.userSettings.aiExperienceSettingsPage.deleteSuccess');
            },
            error: () => this.dialogErrorSource.next('artemisApp.userSettings.aiExperienceSettingsPage.deleteFailure'),
        });
    }

    get hasData(): boolean {
        return this.sessionCount() > 0;
    }

    private loadCounts() {
        this.irisChatHttpService.getSessionAndMessageCount().subscribe({
            next: (counts) => {
                this.sessionCount.set(counts.sessions);
                this.messageCount.set(counts.messages);
            },
            error: () => {
                this.sessionCount.set(0);
            },
        });
    }
}
