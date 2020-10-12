import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
})
export class PlagiarismHeaderComponent {
    @Input() comparisonIdx: number;
    @Output() splitViewChange = new EventEmitter<string>();
    @Output() plagiarismConfirmation = new EventEmitter<boolean>();
}
