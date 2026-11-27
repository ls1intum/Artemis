import { ChangeDetectionStrategy, Component, effect, inject, untracked } from '@angular/core';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { WINDOW_INJECTOR_TOKEN } from 'app/core/interceptor/artemis-version.interceptor';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

/**
 * Takes the place of the AI usage popup when the code of the popup cannot be loaded, for example when a deployment has replaced its file while the
 * tab was open or the connection broke.
 *
 * Without it, the request to open the popup would wait for an answer that never comes: the caller that awaits the choice stays stuck. A request,
 * the one that was made before the load failed and every later one, is answered as dismissed, and the user is offered a reload, which is the only
 * way to get the code again: a failed dynamic import stays failed until the document is loaded anew.
 */
@Component({
    selector: 'jhi-llm-selection-load-error',
    template: '',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LLMSelectionLoadErrorComponent {
    private readonly modalService = inject(LLMSelectionModalService);
    private readonly alertService = inject(AlertService);
    private readonly injectedWindow = inject<Window>(WINDOW_INJECTOR_TOKEN);

    constructor() {
        effect(() => {
            if (this.modalService.isOpen()) {
                untracked(() => this.answerAsDismissed());
            }
        });
    }

    private answerAsDismissed(): void {
        // Report it: a failed chunk would otherwise leave no trace for whoever operates the instance.
        captureException(new Error('The code of the AI usage popup could not be loaded'));
        this.modalService.emitChoice(LLM_MODAL_DISMISSED);
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: 'artemisApp.llmSelectionPopup.loadFailedAlert',
            timeout: 0,
            action: {
                label: 'artemisApp.lazyRouteLoadFailedAction',
                callback: () => this.injectedWindow.location.reload(),
            },
        });
    }
}
