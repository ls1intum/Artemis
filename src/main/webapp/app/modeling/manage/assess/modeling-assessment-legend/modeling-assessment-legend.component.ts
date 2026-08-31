import { Component, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * One canvas highlight the legend explains, so a colour the tutor sees on an element always has a name.
 */
export interface ModelingAssessmentLegendHighlight {
    /** The colour the canvas paints, so the swatch and the element cannot drift apart. */
    color: string;
    /** Translation key of the label. */
    text: string;
}

/**
 * The legend for an assessed Apollon canvas: what the score markers on an element mean, and what any highlight
 * colour on it means.
 *
 * It mounts into the canvas' top-right chrome, beside the diagram it describes, rather than into a pane the tutor
 * has to scroll. Pass the highlights the host actually paints; with none it explains the score markers alone.
 */
@Component({
    selector: 'jhi-modeling-assessment-legend',
    templateUrl: './modeling-assessment-legend.component.html',
    styleUrls: ['./modeling-assessment-legend.component.scss'],
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    host: { class: 'assessment-legend apollon-glass' },
})
export class ModelingAssessmentLegendComponent {
    readonly highlights = input<ModelingAssessmentLegendHighlight[]>([]);

    readonly scoreTones = [
        { text: 'artemisApp.modelingAssessment.legend.positiveScore', icon: faCheck as IconProp, tone: 'positive' },
        { text: 'artemisApp.modelingAssessment.legend.negativeScore', icon: faTimes as IconProp, tone: 'negative' },
        { text: 'artemisApp.modelingAssessment.legend.feedbackWithoutScore', icon: faExclamationTriangle as IconProp, tone: 'zero' },
    ];
}
