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
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { ActivatedRoute } from '@angular/router';
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
});
