import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';

@Component({
    selector: 'jhi-plagiarism-sidebar',
    styleUrls: ['./plagiarism-sidebar.component.scss'],
    templateUrl: './plagiarism-sidebar.component.html',
})
export class PlagiarismSidebarComponent {
    @Input() activeIndex: number;
    @Input() comparisons?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[];

    @Output() selectIndex = new EventEmitter<string>();
}
