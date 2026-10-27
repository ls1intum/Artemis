import { Component, DestroyRef, Injector, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, firstValueFrom } from 'rxjs';
import dayjs from 'dayjs/esm';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AccountService } from 'app/core/auth/account.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { UserService } from 'app/account/user/shared/user.service';
import { TumAetUiButtonComponent, TumAetUiButtonDirective, TumAetUiCardComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-ai-experience-settings',
    imports: [TranslateDirective, ArtemisDatePipe, DeleteButtonDirective, RouterLink, TumAetUiButtonComponent, TumAetUiCardComponent, TumAetUiButtonDirective],
    templateUrl: './ai-experience-settings.component.html',
})
export class AiExperienceSettingsComponent implements OnInit {
    private readonly accountService = inject(AccountService);
    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly injector = inject(Injector);
    private readonly profileService = inject(ProfileService);
    private readonly userService = inject(UserService);
    private readonly alertService = inject(AlertService);
    private readonly llmModalService = inject(LLMSelectionModalService);
    private readonly destroyRef = inject(DestroyRef);

    readonly irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);

    protected readonly ActionType = ActionType;
    protected readonly LLMSelectionDecision = LLMSelectionDecision;

    currentSelection = signal<LLMSelectionDecision | undefined>(undefined);
    selectionDate = signal<dayjs.Dayjs | undefined>(undefined);
    sessionCount = signal(0);
    messageCount = signal(0);
    readonly selectionPending = signal(false);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.updateSelectionFromUser();
        if (this.irisEnabled) {
            this.loadCounts();
        }
    }

    async openSelectionModal(): Promise<void> {
        if (this.selectionPending()) return;
        const userId = this.accountService.userIdentity()?.id;
        this.selectionPending.set(true);
        try {
            const choice = await this.llmModalService.open(this.currentSelection());
            if (this.destroyRef.destroyed || this.accountService.userIdentity()?.id !== userId || choice === LLM_MODAL_DISMISSED) return;
            if (this.irisEnabled) {
                this.injector.get(IrisChatService).updateLLMUsageConsent(choice);
                this.accountService.setUserLLMSelectionDecision(choice);
                this.updateSelectionFromUser();
            } else {
                await firstValueFrom(this.userService.updateLLMSelectionDecision(choice).pipe(takeUntilDestroyed(this.destroyRef)));
                if (this.destroyRef.destroyed || this.accountService.userIdentity()?.id !== userId) return;
                this.accountService.setUserLLMSelectionDecision(choice);
                this.updateSelectionFromUser();
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

    private updateSelectionFromUser() {
        const user = this.accountService.userIdentity();
        this.currentSelection.set(user?.selectedLLMUsage);
        this.selectionDate.set(user?.selectedLLMUsageTimestamp);
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
