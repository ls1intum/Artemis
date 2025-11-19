import { ChangeDetectorRef, Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';

export type LLMSelectionChoice = 'cloud' | 'local' | 'none';

@Component({
    selector: 'jhi-llm-selection-modal',
    templateUrl: './llm-selection-popup.component.html',
    styleUrls: ['./llm-selection-popup.component.scss'],
})
export class LLMSelectionModalComponent implements OnInit {
    private modalService = inject(LLMSelectionModalService);
    private cdr = inject(ChangeDetectorRef);

    @Output() choice = new EventEmitter<LLMSelectionChoice>();

    isVisible = false;

    ngOnInit(): void {
        this.modalService.openModal$.subscribe(() => {
            this.open();
            this.cdr.detectChanges(); // Manuell Change Detection triggern
        });
    }

    open(): void {
        this.isVisible = true;
    }

    close(): void {
        this.isVisible = false;
    }

    selectCloud(): void {
        this.choice.emit('cloud');
        this.modalService.emitChoice('cloud');
        this.close();
    }

    selectLocal(): void {
        this.choice.emit('local');
        this.modalService.emitChoice('local');
        this.close();
    }

    selectNone(): void {
        this.choice.emit('none');
        this.modalService.emitChoice('none');
        this.close();
    }

    onBackdropClick(event: MouseEvent): void {
        this.choice.emit('none');
        this.modalService.emitChoice('none');
        if (event.target === event.currentTarget) {
            this.close();
        }
    }

    onLearnMoreClick(event: MouseEvent): void {
        this.choice.emit('none');
        this.modalService.emitChoice('none');
        event.preventDefault();
    }
}
