import { Component, input } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Subject } from 'rxjs';
import { NgbNav, NgbNavContent, NgbNavItem, NgbNavItemRole, NgbNavLink, NgbNavLinkBase, NgbNavOutlet } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-plagiarism-case-review',
    templateUrl: './plagiarism-case-review.component.html',
    standalone: true,
    imports: [NgbNav, NgbNavItem, NgbNavItemRole, NgbNavLink, NgbNavLinkBase, NgbNavContent, ArtemisPlagiarismModule, NgbNavOutlet, ArtemisSharedCommonModule],
})
export class PlagiarismCaseReviewComponent {
    plagiarismCase = input<PlagiarismCase>();
    forStudent = input(true);

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();
}
