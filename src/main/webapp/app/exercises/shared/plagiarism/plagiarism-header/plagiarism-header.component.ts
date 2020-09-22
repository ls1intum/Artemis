import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
})
export class PlagiarismHeaderComponent {
    @Input() comparisonIdx: number;
    @Output() split = new EventEmitter<string>();
    @Output() tagPlagiarism = new EventEmitter<boolean>();

    onTagPlagiarism(confirmed: boolean) {
        this.tagPlagiarism.emit(confirmed);
    }
}
