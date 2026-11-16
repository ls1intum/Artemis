import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

let nextGroupId = 0;

@Component({
    selector: 'jhi-ai-mode-selector',
    templateUrl: './ai-mode-selector.component.html',
    styleUrl: './ai-mode-selector.component.scss',
    imports: [TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiModeSelectorComponent {
    readonly currentSelection = input<LLMSelectionDecision>();
    readonly isOnPremiseEnabled = input(false);
    readonly disabled = input(false);
    readonly choice = output<LLMSelectionDecision>();
    protected readonly LLMSelectionDecision = LLMSelectionDecision;
    protected readonly groupName = `ai-mode-${nextGroupId++}`;
}
