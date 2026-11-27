import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LLMSelectionLoadErrorComponent } from 'app/logos/llm-selection-load-error.component';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

vi.mock('app/core/error-reporting/capture-exception', () => ({ captureException: vi.fn() }));

describe('LLMSelectionLoadErrorComponent', () => {
    let fixture: ComponentFixture<LLMSelectionLoadErrorComponent>;
    let modalService: LLMSelectionModalService;
    let addAlert: ReturnType<typeof vi.fn>;
    let reload: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        addAlert = vi.fn();
        reload = vi.fn();
        TestBed.configureTestingModule({
            providers: [
                { provide: AlertService, useValue: { addAlert } },
                { provide: WINDOW_INJECTOR_TOKEN, useValue: { location: { reload } } },
            ],
        });
        modalService = TestBed.inject(LLMSelectionModalService);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('answers a request that was made before the load failed as dismissed, so that the caller does not wait for ever', async () => {
        const result = modalService.open(LLMSelectionDecision.CLOUD_AI);

        fixture = TestBed.createComponent(LLMSelectionLoadErrorComponent);
        fixture.detectChanges();

        expect(await result).toBe(LLM_MODAL_DISMISSED);
        expect(modalService.isOpen()).toBe(false);
    });

    it('answers every later request as dismissed as well, because the code cannot be loaded again without a reload', async () => {
        fixture = TestBed.createComponent(LLMSelectionLoadErrorComponent);
        fixture.detectChanges();
        expect(addAlert).not.toHaveBeenCalled();

        const first = modalService.open();
        fixture.detectChanges();
        const second = modalService.open(LLMSelectionDecision.NO_AI);
        fixture.detectChanges();

        expect(await first).toBe(LLM_MODAL_DISMISSED);
        expect(await second).toBe(LLM_MODAL_DISMISSED);
        expect(addAlert).toHaveBeenCalledTimes(2);
    });

    it('does nothing while no request waits', () => {
        fixture = TestBed.createComponent(LLMSelectionLoadErrorComponent);
        fixture.detectChanges();

        expect(addAlert).not.toHaveBeenCalled();
        expect(captureException).not.toHaveBeenCalled();
    });

    it('tells the user and offers a reload, and reports the failure', () => {
        void modalService.open();

        fixture = TestBed.createComponent(LLMSelectionLoadErrorComponent);
        fixture.detectChanges();

        expect(captureException).toHaveBeenCalledOnce();
        expect(addAlert).toHaveBeenCalledOnce();
        const alert = addAlert.mock.calls[0][0];
        expect(alert).toMatchObject({ type: AlertType.DANGER, message: 'artemisApp.llmSelectionPopup.loadFailedAlert', timeout: 0 });
        expect(alert.action.label).toBe('artemisApp.lazyRouteLoadFailedAction');
        expect(reload).not.toHaveBeenCalled();

        alert.action.callback();

        expect(reload).toHaveBeenCalledOnce();
    });
});
