import { Component, EventEmitter, Output } from '@angular/core';

export type LLMSelectionChoice = 'cloud' | 'local' | 'none';

@Component({
    selector: 'app-llm-selection-modal',
    templateUrl: './llm-selection-popup.component.html',
    styleUrls: ['./llm-selection-popup.component.scss'],
})
export class LLMSelectionModalComponent {
    @Output() choice = new EventEmitter<LLMSelectionChoice>();

    isVisible = false;

    open(): void {
        this.isVisible = true;
    }

    close(): void {
        this.isVisible = false;
    }

    selectCloud(): void {
        this.choice.emit('cloud');
        this.close();
    }

    selectLocal(): void {
        this.choice.emit('local');
        this.close();
    }

    selectNone(): void {
        this.choice.emit('none');
        this.close();
    }

    onLearnMoreClick(event: MouseEvent): void {
        event.preventDefault();
        // Learn more clicked
    }

    onBackdropClick(event: MouseEvent): void {
        // Schließen wenn außerhalb geklickt wird
        if (event.target === event.currentTarget) {
            this.close();
        }
    }
}
