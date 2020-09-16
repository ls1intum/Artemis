import { Component, EventEmitter, Output } from '@angular/core';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
})
export class PlagiarismHeaderComponent {
    @Output() newItemEvent = new EventEmitter<void>();

    onConfirmPlagiarism() {
        this.newItemEvent.emit();
    }

    onDenyPlagiarism() {
        this.newItemEvent.emit();
    }
}
