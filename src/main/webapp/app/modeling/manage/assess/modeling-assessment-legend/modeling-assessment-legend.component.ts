import { Component, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faCircleInfo, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';

export interface ModelingAssessmentLegendHighlight {
    color: string;
    text: string;
    info?: string;
}

@Component({
    selector: 'jhi-modeling-assessment-legend',
    templateUrl: './modeling-assessment-legend.component.html',
    styleUrls: ['./modeling-assessment-legend.component.scss'],
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumUiTooltipDirective],
    host: { class: 'assessment-legend apollon-glass' },
})
export class ModelingAssessmentLegendComponent {
    readonly highlights = input<ModelingAssessmentLegendHighlight[]>([]);
    protected readonly faCircleInfo = faCircleInfo;

    readonly scoreTones = [
        { text: 'artemisApp.modelingAssessment.legend.positiveScore', icon: faCheck as IconProp, tone: 'positive' },
        { text: 'artemisApp.modelingAssessment.legend.negativeScore', icon: faTimes as IconProp, tone: 'negative' },
        { text: 'artemisApp.modelingAssessment.legend.feedbackWithoutScore', icon: faExclamationTriangle as IconProp, tone: 'zero' },
    ];
}
