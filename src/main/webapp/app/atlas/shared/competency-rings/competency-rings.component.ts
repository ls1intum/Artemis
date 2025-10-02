import { Component, input } from '@angular/core';
import { faQuestion } from '@fortawesome/free-solid-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-competency-rings',
    templateUrl: './competency-rings.component.html',
    styleUrls: ['./competency-rings.component.scss'],
    standalone: true,
    imports: [NgbTooltip, FaIconComponent, ArtemisTranslatePipe],
})
export class CompetencyRingsComponent {
    progress = input(0);
    mastery = input(0);
    playAnimation = input(true);
    hideTooltip = input(false);
    hideProgress = input(false);

    protected readonly faQuestion = faQuestion;

    get progressPercentage(): number {
        if (this.hideProgress()) {
            return 0;
        }
        return this.percentageRange(this.progress());
    }

    get masteryPercentage(): number {
        if (this.hideProgress()) {
            return 0;
        }
        return this.percentageRange(this.mastery());
    }

    get tooltipText() {
        if (this.hideProgress()) {
            return 'artemisApp.competency.competencyCard.ringsTooltipHideProgress';
        } else {
            return 'artemisApp.competency.competencyCard.ringsTooltip';
        }
    }

    /**
     * Restrict the value to the percentage range (between 0 and 100)
     * @param value
     */
    percentageRange(value: number): number {
        return Math.min(Math.max(value, 0), 100);
    }
}
