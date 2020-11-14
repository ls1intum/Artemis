import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';

@Component({
    selector: 'jhi-plagiarism-sidebar',
    styleUrls: ['./plagiarism-sidebar.component.scss'],
    templateUrl: './plagiarism-sidebar.component.html',
})
export class PlagiarismSidebarComponent {
    @Input() activeIndex: number;
    @Input() comparisons?: PlagiarismComparison[];

    @Output() selectIndex = new EventEmitter<string>();
}
