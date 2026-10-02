import { Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFileAlt } from '@fortawesome/free-regular-svg-icons';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { AssessmentScore } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * The submission panel of an assessment workspace, framed like the workspace's instructions and general feedback & notes
 * panels next to it. Its header names the submission and shows the awarded, deducted and final points of the assessment;
 * a page adds its own tags in front of them (e.g. the word count of a text submission) through `[submissionPanelMeta]`.
 */
@Component({
    selector: 'jhi-assessment-submission-panel',
    templateUrl: './assessment-submission-panel.component.html',
    styleUrls: ['./assessment-submission-panel.component.scss'],
    imports: [FaIconComponent, TumAetUiTagComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class AssessmentSubmissionPanelComponent {
    readonly score = input.required<AssessmentScore>();
    readonly maxPoints = input.required<number>();
    /**
     * Whether the content gets padding and scrolls inside the panel, as text does. Off for content that fills the panel
     * and handles its own scrolling, such as a diagram editor.
     */
    readonly scrollContent = input(true);

    protected readonly faSubmission = faFileAlt;
}
