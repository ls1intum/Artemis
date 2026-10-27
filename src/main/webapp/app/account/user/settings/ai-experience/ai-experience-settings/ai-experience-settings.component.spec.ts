import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AiExperienceSettingsComponent } from './ai-experience-settings.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockDirective, MockProvider } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Subject, of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { UserService } from 'app/account/user/shared/user.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('AiExperienceSettingsComponent', () => {
    let component: AiExperienceSettingsComponent;
    let fixture: ComponentFixture<AiExperienceSettingsComponent>;
    let irisChatHttpService: IrisChatHttpService;
    let irisChatService: IrisChatService;
    let accountService: AccountService;
    let alertService: AlertService;
    let llmModalService: LLMSelectionModalService;

    const createComponent = () => {
        fixture = TestBed.createComponent(AiExperienceSettingsComponent);
        component = fixture.componentInstance;
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AiExperienceSettingsComponent],
            providers: [
                MockProvider(ProfileService, { isModuleFeatureActive: () => true }),
                MockProvider(UserService),
                MockProvider(IrisChatHttpService),
                MockProvider(IrisChatService),
                MockProvider(TranslateService),
                MockProvider(AlertService),
                MockProvider(LLMSelectionModalService),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        })
            .overrideComponent(AiExperienceSettingsComponent, {
                remove: { imports: [TranslateDirective, DeleteButtonDirective] },
                add: { imports: [MockDirective(TranslateDirective), MockDirective(DeleteButtonDirective)] },
            })
            .compileComponents();

        irisChatHttpService = TestBed.inject(IrisChatHttpService);
        irisChatService = TestBed.inject(IrisChatService);
        accountService = TestBed.inject(AccountService);
        accountService.userIdentity.set({ id: 1, internal: true, selectedLLMUsage: LLMSelectionDecision.NO_AI });
        alertService = TestBed.inject(AlertService);
        llmModalService = TestBed.inject(LLMSelectionModalService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 0, messages: 0 }));
        createComponent();
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should load session and message counts on init', () => {
        const countSpy = vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 5, messages: 42 }));
        createComponent();
        fixture.detectChanges();

        expect(countSpy).toHaveBeenCalledOnce();
        expect(component.sessionCount()).toBe(5);
        expect(component.messageCount()).toBe(42);
    });

    it('should handle zero counts', () => {
        vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 0, messages: 0 }));
        createComponent();
        fixture.detectChanges();

        expect(component.sessionCount()).toBe(0);
        expect(component.messageCount()).toBe(0);
    });

    it('should delete all Iris interactions successfully', () => {
        vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 3, messages: 10 }));
        createComponent();
        fixture.detectChanges();

        const deleteChatSpy = vi.spyOn(irisChatHttpService, 'deleteAllSessions').mockReturnValue(of(new HttpResponse<void>({ status: 204 })));
        const alertSpy = vi.spyOn(alertService, 'success');

        component.deleteAllIrisInteractions();

        expect(deleteChatSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.userSettings.aiExperienceSettingsPage.deleteSuccess');
        expect(component.sessionCount()).toBe(0);
        expect(component.messageCount()).toBe(0);
    });

    it('should handle delete failure when chat deletion fails', () => {
        vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 3, messages: 10 }));
        createComponent();
        fixture.detectChanges();

        vi.spyOn(irisChatHttpService, 'deleteAllSessions').mockReturnValue(throwError(() => new Error('error')));

        const dialogErrorSpy = vi.fn();
        component.dialogError$.subscribe(dialogErrorSpy);

        component.deleteAllIrisInteractions();

        expect(dialogErrorSpy).toHaveBeenCalledWith('artemisApp.userSettings.aiExperienceSettingsPage.deleteFailure');
        expect(component.sessionCount()).toBe(3);
    });

    it('should open selection modal and update on choice', async () => {
        vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 0, messages: 0 }));
        createComponent();
        fixture.detectChanges();

        const openSpy = vi.spyOn(llmModalService, 'open').mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
        const updateConsentSpy = vi.spyOn(irisChatService, 'updateLLMUsageConsent').mockImplementation(() => {});
        const setDecisionSpy = vi.spyOn(accountService, 'setUserLLMSelectionDecision').mockImplementation(() => {});

        await component.openSelectionModal();

        expect(openSpy).toHaveBeenCalled();
        expect(updateConsentSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        expect(setDecisionSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
    });

    it('should render delete button when sessions exist', () => {
        vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 3, messages: 10 }));
        createComponent();
        fixture.detectChanges();

        const deleteButton = fixture.debugElement.query(By.directive(DeleteButtonDirective));
        expect(deleteButton).toBeTruthy();
    });

    it('should not render delete button when no data exists', () => {
        vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 0, messages: 0 }));
        createComponent();
        fixture.detectChanges();

        const deleteButton = fixture.debugElement.query(By.directive(DeleteButtonDirective));
        expect(deleteButton).toBeNull();
    });

    it('should not update when modal is dismissed', async () => {
        vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount').mockReturnValue(of({ sessions: 0, messages: 0 }));
        createComponent();
        fixture.detectChanges();

        vi.spyOn(llmModalService, 'open').mockResolvedValue(LLM_MODAL_DISMISSED);
        const updateConsentSpy = vi.spyOn(irisChatService, 'updateLLMUsageConsent');
        const setDecisionSpy = vi.spyOn(accountService, 'setUserLLMSelectionDecision');

        await component.openSelectionModal();

        expect(updateConsentSpy).not.toHaveBeenCalled();
        expect(setDecisionSpy).not.toHaveBeenCalled();
    });
    it('saves a Home-only AI preference without initializing or requesting Iris', async () => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(false);
        const counts = vi.spyOn(irisChatHttpService, 'getSessionAndMessageCount');
        const userService = TestBed.inject(UserService);
        const save = vi.spyOn(userService, 'updateLLMSelectionDecision').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
        const iris = vi.spyOn(irisChatService, 'updateLLMUsageConsent');
        vi.spyOn(llmModalService, 'open').mockResolvedValue(LLMSelectionDecision.LOCAL_AI);
        createComponent();
        fixture.detectChanges();
        expect(counts).not.toHaveBeenCalled();
        await component.openSelectionModal();
        expect(save).toHaveBeenCalledExactlyOnceWith(LLMSelectionDecision.LOCAL_AI);
        expect(iris).not.toHaveBeenCalled();
    });
    it('keeps the previous preference and reports a failed Home-only save', async () => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(false);
        vi.spyOn(TestBed.inject(UserService), 'updateLLMSelectionDecision').mockReturnValue(throwError(() => new Error('offline')));
        vi.spyOn(llmModalService, 'open').mockResolvedValue(LLMSelectionDecision.NO_AI);
        const account = vi.spyOn(accountService, 'setUserLLMSelectionDecision');
        const error = vi.spyOn(alertService, 'error');
        createComponent();
        fixture.detectChanges();
        await component.openSelectionModal();
        expect(account).not.toHaveBeenCalled();
        expect(error).toHaveBeenCalledExactlyOnceWith('artemisApp.userSettings.aiExperienceSettingsPage.updateFailure');
    });
    it('prevents overlapping Home-only preference dialogs and saves', async () => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(false);
        const response = new Subject<HttpResponse<void>>();
        const save = vi.spyOn(TestBed.inject(UserService), 'updateLLMSelectionDecision').mockReturnValue(response);
        const open = vi.spyOn(llmModalService, 'open').mockResolvedValueOnce(LLMSelectionDecision.CLOUD_AI).mockResolvedValueOnce(LLMSelectionDecision.NO_AI);
        createComponent();
        fixture.detectChanges();
        const first = component.openSelectionModal();
        await Promise.resolve();
        const second = component.openSelectionModal();
        await Promise.resolve();
        const pendingSaveCount = save.mock.calls.length;
        response.next(new HttpResponse({ status: 200 }));
        response.complete();
        await Promise.all([first, second]);
        expect(open).toHaveBeenCalledOnce();
        expect(pendingSaveCount).toBe(1);
        expect(component.currentSelection()).toBe(LLMSelectionDecision.CLOUD_AI);
    });
    it('does not apply a pending preference save to a different account', async () => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(false);
        const response = new Subject<HttpResponse<void>>();
        vi.spyOn(TestBed.inject(UserService), 'updateLLMSelectionDecision').mockReturnValue(response);
        vi.spyOn(llmModalService, 'open').mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
        createComponent();
        fixture.detectChanges();
        const saving = component.openSelectionModal();
        await Promise.resolve();
        accountService.userIdentity.set({ id: 2, internal: true, selectedLLMUsage: LLMSelectionDecision.NO_AI });
        response.next(new HttpResponse({ status: 200 }));
        await saving;
        expect(accountService.userIdentity()?.selectedLLMUsage).toBe(LLMSelectionDecision.NO_AI);
    });
    it.each(['account change', 'destruction'])('discards an open preference dialog after %s', async (change) => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(false);
        let choose!: (choice: LLMSelectionDecision) => void;
        vi.spyOn(llmModalService, 'open').mockReturnValue(new Promise((resolve) => (choose = resolve)));
        const save = vi.spyOn(TestBed.inject(UserService), 'updateLLMSelectionDecision');
        createComponent();
        fixture.detectChanges();
        const choosing = component.openSelectionModal();
        if (change === 'account change') accountService.userIdentity.set({ id: 2, internal: true, selectedLLMUsage: LLMSelectionDecision.NO_AI });
        else fixture.destroy();
        choose(LLMSelectionDecision.CLOUD_AI);
        await choosing;
        expect(save).not.toHaveBeenCalled();
        expect(accountService.userIdentity()?.selectedLLMUsage).toBe(LLMSelectionDecision.NO_AI);
    });
    it('cancels a pending preference save when the settings page is destroyed', async () => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(false);
        const response = new Subject<HttpResponse<void>>();
        vi.spyOn(TestBed.inject(UserService), 'updateLLMSelectionDecision').mockReturnValue(response);
        vi.spyOn(llmModalService, 'open').mockResolvedValue(LLMSelectionDecision.CLOUD_AI);
        const error = vi.spyOn(alertService, 'error');
        createComponent();
        fixture.detectChanges();
        const saving = component.openSelectionModal();
        await Promise.resolve();
        fixture.destroy();
        response.next(new HttpResponse({ status: 200 }));
        await saving;
        expect(accountService.userIdentity()?.selectedLLMUsage).toBe(LLMSelectionDecision.NO_AI);
        expect(error).not.toHaveBeenCalled();
    });
});
