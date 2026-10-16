import { Component, input, model, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCommentDots, faFlag, faLock, faPlus, faTerminal } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabsComponent, TumAetUiTabsSurface } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { CollapsedPanelRailItem } from 'app/shared-ui/components/collapsed-panel-rail/collapsed-panel-rail.component';

/** The tabs of an assessment's general feedback and notes panel. */
export type AssessmentDetailsTab = 'buildOutput' | 'feedback' | 'notes' | 'complaint';

export function isAssessmentDetailsTab(value: string): value is AssessmentDetailsTab {
    return value === 'buildOutput' || value === 'feedback' || value === 'notes' || value === 'complaint';
}

/**
 * The icons of a collapsed general feedback and notes panel: one for each tab the panel has, with the icon of the tab, so the rail
 * shows what the panel holds and a click opens the panel at that tab (the id is the tab's value).
 */
export function assessmentDetailsRailItems(options: { showBuildOutput?: boolean; complaintType?: ComplaintType } = {}): CollapsedPanelRailItem[] {
    return [
        ...(options.showBuildOutput ? [{ id: 'buildOutput', icon: faTerminal, labelKey: 'artemisApp.editor.buildOutput' }] : []),
        { id: 'feedback', icon: faCommentDots, labelKey: 'artemisApp.assessment.generalFeedback' },
        { id: 'notes', icon: faLock, labelKey: 'artemisApp.assessment.detail.notesTab' },
        ...(options.complaintType
            ? [
                  {
                      id: 'complaint',
                      icon: faFlag,
                      labelKey:
                          options.complaintType === ComplaintType.MORE_FEEDBACK
                              ? 'artemisApp.assessment.detail.studentRequestTab'
                              : 'artemisApp.assessment.detail.studentComplaintTab',
                  },
              ]
            : []),
    ];
}

/**
 * The header of an assessment's general feedback and notes panel: a tab for the build output of a programming submission,
 * one for the general feedback, one for the private tutor note, one for the student's complaint or more feedback request
 * if there is one, and a plus button that adds a general feedback. The page renders the content of the open tab itself.
 */
@Component({
    selector: 'jhi-assessment-details-header',
    templateUrl: './assessment-details-header.component.html',
    styleUrls: ['./assessment-details-header.component.scss'],
    imports: [FaIconComponent, TumAetUiTabsComponent, TumAetUiTabListComponent, TumAetUiTabComponent, ArtemisTranslatePipe],
})
export class AssessmentDetailsHeaderComponent {
    readonly activeTab = model<AssessmentDetailsTab>('feedback');
    /** How many general feedback the assessment has, shown on its tab. */
    readonly feedbackCount = input(0);
    /** Whether the note has text, marked on its tab so a note is noticed while the general feedback is open. */
    readonly hasNote = input(false);
    readonly disabled = input(false);
    /**
     * The surface of the panel header the tabs sit on, which their scroll buttons blend into: the `muted` header of the
     * assessment workspace, or the `content` header of the code editor's panels.
     */
    readonly surface = input<TumAetUiTabsSurface>('muted');
    /** Whether the panel has a build output tab, which leads the tabs; only a programming assessment has one. */
    readonly showBuildOutput = input(false);
    /** The type of the student's complaint or more feedback request, which adds a tab for it; none without one. */
    readonly complaintType = input<ComplaintType | undefined>(undefined);
    /** Whether the complaint still awaits a response, marked on its tab so it is noticed while another tab is open. */
    readonly complaintAwaitsResponse = input(false);

    readonly addGeneralFeedback = output<void>();

    protected readonly ComplaintType = ComplaintType;
    protected readonly faCommentDots = faCommentDots;
    protected readonly faTerminal = faTerminal;
    protected readonly faFlag = faFlag;
    protected readonly faLock = faLock;
    protected readonly faPlus = faPlus;

    selectTab(value: number | string | undefined): void {
        if (value === 'feedback' || value === 'notes' || (value === 'complaint' && this.complaintType()) || (value === 'buildOutput' && this.showBuildOutput())) {
            this.activeTab.set(value);
        }
    }
}
