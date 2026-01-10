import { Component, input, output } from '@angular/core';

// Simple mock to avoid ng-mocks issues with signal-based viewChild
// Kept in separate file to avoid Jest bug #15175 where signal inputs
// in spec files break jest.mock()
@Component({
    selector: 'jhi-iris-base-chatbot',
    template: '',
    standalone: true,
})
export class MockIrisBaseChatbotComponent {
    readonly fullSize = input<boolean>();
    readonly showCloseButton = input<boolean>();
    readonly isChatGptWrapper = input<boolean>();
    readonly isChatHistoryAvailable = input<boolean>();
    readonly fullSizeToggle = output<void>();
    readonly closeClicked = output<void>();
}
