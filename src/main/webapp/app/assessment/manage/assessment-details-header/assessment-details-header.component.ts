import { Component, input, model, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCommentDots, faFlag, faLock, faPlus } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabsComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';

/** The tabs of an assessment's general feedback and notes panel. */
export type AssessmentDetailsTab = 'feedback' | 'notes' | 'complaint';

/**
 * The header of an assessment's general feedback and notes panel: a tab for the general feedback, one for the private
 * tutor note, one for the student's complaint or more feedback request if there is one, and a plus button that adds a
 * general feedback. The page renders the content of the open tab itself.
 */
@Component({
    selector: 'jhi-assessment-details-header',
    templateUrl: './assessment-details-header.component.html',
    styleUrls: ['./assessment-details-header.component.scss'],
    imports: [FaIconComponent, TumAetUiButtonDirective, TumAetUiTabsComponent, TumAetUiTabListComponent, TumAetUiTabComponent, TumAetUiTooltipDirective, ArtemisTranslatePipe],
})
export class AssessmentDetailsHeaderComponent {
    readonly activeTab = model<AssessmentDetailsTab>('feedback');
    /** How many general feedback the assessment has, shown on its tab. */
    readonly feedbackCount = input(0);
    /** Whether the note has text, marked on its tab so a note is noticed while the general feedback is open. */
    readonly hasNote = input(false);
    readonly disabled = input(false);
    /** The type of the student's complaint or more feedback request, which adds a tab for it; none without one. */
    readonly complaintType = input<ComplaintType | undefined>(undefined);
    /** Whether the complaint still awaits a response, marked on its tab so it is noticed while another tab is open. */
    readonly complaintAwaitsResponse = input(false);

    readonly addGeneralFeedback = output<void>();

    protected readonly ComplaintType = ComplaintType;
    protected readonly faCommentDots = faCommentDots;
    protected readonly faFlag = faFlag;
    protected readonly faLock = faLock;
    protected readonly faPlus = faPlus;

    selectTab(value: number | string | undefined): void {
        if (value === 'feedback' || value === 'notes' || (value === 'complaint' && this.complaintType())) {
            this.activeTab.set(value);
        }
    }
}
