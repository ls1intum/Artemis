import { Component, input, output } from '@angular/core';

// Simple mock to avoid ng-mocks issues with signal-based viewChild
// Kept in separate file because signal inputs in spec files can break mock resolution
@Component({
    selector: 'jhi-iris-base-chatbot',
    template: '',
    standalone: true,
})
export class MockIrisBaseChatbotComponent {
    readonly layout = input<'client' | 'widget'>('client');
    readonly fullSize = input<boolean>();
    readonly showCloseButton = input<boolean>();
    readonly isChatGptWrapper = input<boolean>();
    readonly isChatHistoryAvailable = input<boolean>();
    readonly fullSizeToggle = output<void>();
    readonly closeClicked = output<void>();
}
