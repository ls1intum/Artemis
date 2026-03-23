import { Component, input } from '@angular/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-competency-rings',
    templateUrl: './competency-rings.component.html',
    styleUrls: ['./competency-rings.component.scss'],
    standalone: true,
    imports: [NgbTooltip, ArtemisTranslatePipe],
})
export class CompetencyRingsComponent {
    progress = input(0);
    mastery = input(0);
    playAnimation = input(true);
    hideTooltip = input(false);

    get progressPercentage(): number {
        return this.percentageRange(this.progress());
    }

    get masteryPercentage(): number {
        return this.percentageRange(this.mastery());
    }

    get tooltipText() {
        return 'artemisApp.competency.competencyCard.ringsTooltip';
    }

    /**
     * Restrict the value to the percentage range (between 0 and 100)
     * @param value
     */
    percentageRange(value: number): number {
        return Math.min(Math.max(value, 0), 100);
    }
}
