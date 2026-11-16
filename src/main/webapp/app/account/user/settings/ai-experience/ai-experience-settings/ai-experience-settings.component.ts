import { Component, OnInit, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
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
import { TumAetUiButtonComponent, TumAetUiButtonDirective, TumAetUiCardComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-ai-experience-settings',
    imports: [TranslateDirective, ArtemisDatePipe, DeleteButtonDirective, RouterLink, TumAetUiButtonComponent, TumAetUiCardComponent, TumAetUiButtonDirective],
    templateUrl: './ai-experience-settings.component.html',
})
export class AiExperienceSettingsComponent implements OnInit {
    private readonly accountService = inject(AccountService);
    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly irisChatService = inject(IrisChatService);
    private readonly alertService = inject(AlertService);
    private readonly llmModalService = inject(LLMSelectionModalService);

    protected readonly ActionType = ActionType;
    protected readonly LLMSelectionDecision = LLMSelectionDecision;

    currentSelection = signal<LLMSelectionDecision | undefined>(undefined);
    selectionDate = signal<dayjs.Dayjs | undefined>(undefined);
    sessionCount = signal(0);
    messageCount = signal(0);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.updateSelectionFromUser();
        this.loadCounts();
    }

    async openSelectionModal(): Promise<void> {
        const choice = await this.llmModalService.open(this.currentSelection());

        if (choice && choice !== LLM_MODAL_DISMISSED) {
            this.irisChatService.updateLLMUsageConsent(choice);
            this.accountService.setUserLLMSelectionDecision(choice);
            this.updateSelectionFromUser();
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
