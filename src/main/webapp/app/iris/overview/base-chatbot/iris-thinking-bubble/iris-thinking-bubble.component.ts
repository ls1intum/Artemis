import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
    selector: 'jhi-iris-thinking-bubble',
    templateUrl: './iris-thinking-bubble.component.html',
    styleUrl: './iris-thinking-bubble.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisThinkingBubbleComponent {
    readonly message = input.required<string>();
}
