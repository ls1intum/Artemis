import { Component, Input } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-plagiarism-case-verdict',
    templateUrl: './plagiarism-case-verdict.component.html',
    imports: [NgbTooltip, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class PlagiarismCaseVerdictComponent {
    @Input() plagiarismCase: PlagiarismCase;
    @Input() hideDetails = false;

    readonly plagiarismVerdict = PlagiarismVerdict;

    get verdictTranslationString(): string {
        switch (this.plagiarismCase.verdict) {
            case PlagiarismVerdict.PLAGIARISM: {
                return 'artemisApp.plagiarism.plagiarismCases.verdict.plagiarism';
            }
            case PlagiarismVerdict.POINT_DEDUCTION: {
                return 'artemisApp.plagiarism.plagiarismCases.verdict.pointDeduction';
            }
            case PlagiarismVerdict.WARNING: {
                return 'artemisApp.plagiarism.plagiarismCases.verdict.warning';
            }
            case PlagiarismVerdict.NO_PLAGIARISM: {
                return 'artemisApp.plagiarism.plagiarismCases.verdict.noPlagiarism';
            }
            default: {
                return 'artemisApp.plagiarism.plagiarismCases.verdict.none';
            }
        }
    }
}
