import { Component, input, model, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCommentDots, faLock, faPlus } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabsComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** The tabs of an assessment's general feedback and notes panel. */
export type AssessmentDetailsTab = 'feedback' | 'notes';

/**
 * The header of an assessment's general feedback and notes panel: a tab for the general feedback and one for the private
 * tutor note, and a plus button that adds a general feedback. The page renders the content of the open tab itself.
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

    readonly addGeneralFeedback = output<void>();

    protected readonly faCommentDots = faCommentDots;
    protected readonly faLock = faLock;
    protected readonly faPlus = faPlus;

    selectTab(value: number | string | undefined): void {
        if (value === 'feedback' || value === 'notes') {
            this.activeTab.set(value);
        }
    }
}
