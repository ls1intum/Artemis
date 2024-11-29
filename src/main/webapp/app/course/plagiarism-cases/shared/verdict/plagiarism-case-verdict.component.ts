import { Component, input } from '@angular/core';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-plagiarism-case-verdict',
    templateUrl: './plagiarism-case-verdict.component.html',
    standalone: true,
    imports: [NgbTooltip, ArtemisSharedCommonModule],
})
export class PlagiarismCaseVerdictComponent {
    plagiarismCase = input.required<PlagiarismCase>();
    hideDetails = input(false);

    readonly plagiarismVerdict = PlagiarismVerdict;

    get verdictTranslationString(): string {
        switch (this.plagiarismCase().verdict) {
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
