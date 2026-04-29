import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/shared/service/alert.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';

@Component({
    selector: 'jhi-ai-experience-settings',
    imports: [TranslateDirective, ArtemisDatePipe, DeleteButtonDirective, RouterLink],
    templateUrl: './ai-experience-settings.component.html',
})
export class AiExperienceSettingsComponent implements OnInit {
    private readonly accountService = inject(AccountService);
    private readonly userService = inject(UserService);
    private readonly irisChatHttpService = inject(IrisChatHttpService);
    private readonly irisMemoriesHttpService = inject(IrisMemoriesHttpService);
    private readonly alertService = inject(AlertService);
    private readonly llmModalService = inject(LLMSelectionModalService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly ActionType = ActionType;
    protected readonly LLMSelectionDecision = LLMSelectionDecision;

    currentSelection = signal<LLMSelectionDecision | undefined>(undefined);
    selectionDate = signal<dayjs.Dayjs | undefined>(undefined);
    sessionCount = signal(0);
    messageCount = signal(0);
    memoryCount = signal(0);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.updateSelectionFromUser();
        this.loadCounts();
    }

    async openSelectionModal(): Promise<void> {
        const choice = await this.llmModalService.open(this.currentSelection());

        if (choice && choice !== LLM_MODAL_DISMISSED) {
            // Persist server-side, then mirror to local account state. Settings has no chat-host
            // concerns, so we go directly through userService + accountService instead of routing
            // through a chat controller.
            this.userService
                .updateLLMSelectionDecision(choice)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({
                    next: () => {
                        this.accountService.setUserLLMSelectionDecision(choice);
                        this.updateSelectionFromUser();
                    },
                    error: () => {
                        this.alertService.error('artemisApp.userSettings.aiExperienceSettingsPage.updateFailed');
                    },
                });
        }
    }

    deleteAllIrisInteractions() {
        forkJoin([
            this.irisChatHttpService.deleteAllSessions().pipe(catchError(() => of('error'))),
            this.irisMemoriesHttpService.deleteAllUserMemories().pipe(catchError(() => of('error'))),
        ]).subscribe({
            next: ([sessionsResult, memoriesResult]) => {
                const sessionsDeleted = sessionsResult !== 'error';
                const memoriesDeleted = memoriesResult !== 'error';

                if (sessionsDeleted) {
                    this.sessionCount.set(0);
                    this.messageCount.set(0);
                }
                if (memoriesDeleted) {
                    this.memoryCount.set(0);
                }

                if (sessionsDeleted && memoriesDeleted) {
                    this.dialogErrorSource.next('');
                    this.alertService.success('artemisApp.userSettings.aiExperienceSettingsPage.deleteSuccess');
                } else {
                    this.dialogErrorSource.next('artemisApp.userSettings.aiExperienceSettingsPage.deleteFailure');
                }
            },
        });
    }

    get hasData(): boolean {
        return this.sessionCount() > 0 || this.memoryCount() > 0;
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

        this.irisMemoriesHttpService.getUserMemoryCount().subscribe({
            next: (count) => this.memoryCount.set(count),
            error: () => this.memoryCount.set(0),
        });
    }
}
