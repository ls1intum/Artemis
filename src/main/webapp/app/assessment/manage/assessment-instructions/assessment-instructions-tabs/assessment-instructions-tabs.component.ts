import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabsComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';

/**
 * The tabs of a tabbed `jhi-assessment-instructions`, drawn in the header of the panel that holds it instead of above its content, so the
 * header of the panel is the tabs rather than a title with tabs below it. The page hands over the instructions it wants to drive
 * (`<jhi-assessment-instructions #instructions [tabbed]="true" />` and `<jhi-assessment-instructions-tabs [instructions]="instructions" />`).
 */
@Component({
    selector: 'jhi-assessment-instructions-tabs',
    templateUrl: './assessment-instructions-tabs.component.html',
    styleUrl: './assessment-instructions-tabs.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumAetUiTabsComponent, TumAetUiTabListComponent, TumAetUiTabComponent],
})
export class AssessmentInstructionsTabsComponent {
    /** The instructions whose sections these tabs open. */
    readonly instructions = input.required<AssessmentInstructionsComponent>();
}
