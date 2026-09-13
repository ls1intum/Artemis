import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { faGaugeHigh, faLayerGroup, faSliders, faUserCheck, faWandMagicSparkles, faXmark } from '@fortawesome/free-solid-svg-icons';
import { faClock } from '@fortawesome/free-regular-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AthenaLogoComponent } from 'app/shared-ui/athena-logo/athena-logo.component';

interface FeatureCard {
    titleKey: string;
    descKey: string;
    icon: IconDefinition;
}

/**
 * Informational modal explaining what Athena does, opened from the "Learn more" link on the course overview's Athena
 * card. Purely read-only: unlike {@link AboutIrisModalComponent} there is no "try it" action, because Athena has
 * nothing to start a session in.
 */
@Component({
    selector: 'jhi-about-athena-modal',
    templateUrl: './about-athena-modal.component.html',
    styleUrl: './about-athena-modal.component.scss',
    imports: [AthenaLogoComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AboutAthenaModalComponent {
    // Opened through PrimeNG's DynamicDialog (DialogService) from the course overview.
    private readonly dynamicDialogRef = inject(DynamicDialogRef, { optional: true });

    protected readonly faXmark = faXmark;

    protected readonly whatAthenaCanDo: FeatureCard[] = [
        {
            titleKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.gradingFeedbackTitle',
            descKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.gradingFeedbackDesc',
            icon: faWandMagicSparkles,
        },
        {
            titleKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.formativeFeedbackTitle',
            descKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.formativeFeedbackDesc',
            icon: faClock,
        },
        {
            titleKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.exerciseTypesTitle',
            descKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.exerciseTypesDesc',
            icon: faLayerGroup,
        },
    ];

    protected readonly whatToExpect: FeatureCard[] = [
        {
            titleKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.suggestionsNotGradesTitle',
            descKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.suggestionsNotGradesDesc',
            icon: faUserCheck,
        },
        {
            titleKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.requestLimitTitle',
            descKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.requestLimitDesc',
            icon: faGaugeHigh,
        },
        {
            titleKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.configurablePerFeatureTitle',
            descKey: 'artemisApp.course.athenaConfig.aboutAthenaModal.configurablePerFeatureDesc',
            icon: faSliders,
        },
    ];

    close(): void {
        this.dynamicDialogRef?.close();
    }
}
