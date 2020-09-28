import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'jhi-plagiarism-header',
    styleUrls: ['./plagiarism-header.component.scss'],
    templateUrl: './plagiarism-header.component.html',
})
export class PlagiarismHeaderComponent {
    @Input() comparisonIdx: number;
    @Output() split = new EventEmitter<string>();
    @Output() tag = new EventEmitter<boolean>();

    tagPlagiarism(confirmed: boolean) {
        this.tag.emit(confirmed);
    }
}
