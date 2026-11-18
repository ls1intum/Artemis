import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGhost } from '@fortawesome/free-solid-svg-icons';
import { AiModeSelectorComponent } from 'app/logos/ai-mode-selector.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Component, DestroyRef, Injector, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, firstValueFrom } from 'rxjs';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { UserService } from 'app/account/user/shared/user.service';
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
    private readonly injector = inject(Injector);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);
    private readonly userService = inject(UserService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly ActionType = ActionType;
    protected readonly faGhost = faGhost;

    /** Without the Iris module, the preference is only used by the Home page, and nothing of Iris is created or requested. */
    readonly irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    readonly selectionPending = signal(false);
    readonly isSavingConsent = computed(() => this.selectionPending() || (this.irisEnabled && this.injector.get(IrisChatService).isSavingLLMUsageConsent()));
    readonly currentSelection = computed(() => this.accountService.userIdentity()?.selectedLLMUsage);
    readonly isOnPremiseEnabled = this.profileService.isLLMDeploymentEnabled();
    sessionCount = signal(0);
    messageCount = signal(0);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        if (this.irisEnabled) {
            this.loadCounts();
        }
    }

    selectMode(choice: LLMSelectionDecision): void {
        if (choice === this.currentSelection()) {
            return;
        }
        if (this.irisEnabled) {
            this.injector.get(IrisChatService).updateLLMUsageConsent(choice);
        } else {
            void this.saveSelectionWithoutIris(choice);
        }
    }

    /** Saves the preference of an installation without Iris, once at a time, and applies it only to the account and the page that asked for it. */
    private async saveSelectionWithoutIris(choice: LLMSelectionDecision): Promise<void> {
        if (this.selectionPending()) {
            return;
        }
        const userId = this.accountService.userIdentity()?.id;
        this.selectionPending.set(true);
        try {
            await firstValueFrom(this.userService.updateLLMSelectionDecision(choice).pipe(takeUntilDestroyed(this.destroyRef)));
            if (!this.destroyRef.destroyed && this.accountService.userIdentity()?.id === userId) {
                this.accountService.setUserLLMSelectionDecision(choice);
            }
        } catch {
            if (!this.destroyRef.destroyed && this.accountService.userIdentity()?.id === userId) {
                this.alertService.error('artemisApp.userSettings.aiExperienceSettingsPage.updateFailure');
            }
        } finally {
            this.selectionPending.set(false);
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
