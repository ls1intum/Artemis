import { Component, Input } from '@angular/core';
import { PlagiarismCase } from 'app/plagiarism/shared/types/PlagiarismCase';
import { Subject } from 'rxjs';
import { NgbNav, NgbNavContent, NgbNavItem, NgbNavItemRole, NgbNavLink, NgbNavLinkBase, NgbNavOutlet } from '@ng-bootstrap/ng-bootstrap';
import { PlagiarismSplitViewComponent } from 'app/plagiarism/manage/plagiarism-split-view/plagiarism-split-view.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-plagiarism-case-review',
    templateUrl: './plagiarism-case-review.component.html',
    imports: [NgbNav, NgbNavItem, NgbNavItemRole, NgbNavLink, NgbNavLinkBase, NgbNavContent, PlagiarismSplitViewComponent, NgbNavOutlet, ArtemisTranslatePipe],
})
export class PlagiarismCaseReviewComponent {
    @Input() plagiarismCase: PlagiarismCase;
    @Input() forStudent = true;

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
}
