import { Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFileAlt } from '@fortawesome/free-regular-svg-icons';
import { AssessmentScore } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AssessmentScoreTagsComponent } from 'app/assessment/manage/assessment-score-tags/assessment-score-tags.component';

/**
 * The submission panel of an assessment workspace, framed like the workspace's instructions and general feedback & notes
 * panels next to it. Its header names the submission and shows the awarded, deducted and final points of the assessment;
 * a page adds its own tags in front of them (e.g. the word count of a text submission) through `[submissionPanelMeta]`.
 */
@Component({
    selector: 'jhi-assessment-submission-panel',
    templateUrl: './assessment-submission-panel.component.html',
    styleUrls: ['./assessment-submission-panel.component.scss'],
    imports: [FaIconComponent, TranslateDirective, AssessmentScoreTagsComponent],
})
export class AssessmentSubmissionPanelComponent {
    /** The awarded, deducted and final points shown in the header; a page that shows the points elsewhere leaves it out. */
    readonly score = input<AssessmentScore>();
    readonly maxPoints = input(0);
    /**
     * Whether the content gets padding and scrolls inside the panel, as text does. Off for content that fills the panel
     * and handles its own scrolling, such as a diagram editor.
     */
    readonly scrollContent = input(true);
    /** The panel's title; the student's view of an assessed submission names the assessment rather than the submission. */
    readonly titleKey = input('artemisApp.result.submission');
    /**
     * Whether the panel fills a surface that frames it already, such as the editor side of the exercise page: it then drops
     * its own border and rounded corners, which would otherwise sit inside the surface's and leave its background in the
     * corners.
     */
    readonly flush = input(false);

    protected readonly faSubmission = faFileAlt;
}
