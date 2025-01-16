import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { Subscription } from 'rxjs';
import { NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-included-in-score-badge',
    templateUrl: './included-in-score-badge.component.html',
    styleUrls: ['./included-in-score-badge.component.scss'],
    imports: [NgClass, NgbTooltip],
})
export class IncludedInScoreBadgeComponent implements OnInit, OnDestroy, OnChanges {
    private translateService = inject(TranslateService);

    @Input() includedInOverallScore: IncludedInOverallScore | undefined;
    public translatedEnum = '';
    public translatedTooltip = '';
    public badgeClass: string;
    private translateSubscription: Subscription;

    /**
     * Sets the badge attributes based on the included in score enum
     */
    ngOnInit(): void {
        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            this.setBadgeAttributes();
        });
    }

    ngOnChanges() {
        this.setBadgeAttributes();
    }

    /**
     * Cleans up the subscription to the translation service
     */
    ngOnDestroy(): void {
        if (this.translateSubscription) {
            this.translateSubscription.unsubscribe();
        }
    }

    private setBadgeAttributes(): void {
        if (!this.includedInOverallScore) {
            return;
        }

        switch (this.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                this.badgeClass = 'bg-warning';
                this.translatedEnum = this.translateService.instant('artemisApp.exercise.includedAsBonus');
                this.translatedTooltip = this.translateService.instant('artemisApp.exercise.includedAsBonusTooltip');
                break;
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                this.badgeClass = 'bg-success';
                this.translatedEnum = this.translateService.instant('artemisApp.exercise.includedCompletely');
                this.translatedTooltip = this.translateService.instant('artemisApp.exercise.includedCompletelyTooltip');
                break;
            case IncludedInOverallScore.NOT_INCLUDED:
                this.badgeClass = 'bg-secondary';
                this.translatedEnum = this.translateService.instant('artemisApp.exercise.notIncluded');
                this.translatedTooltip = this.translateService.instant('artemisApp.exercise.notIncludedTooltip');
                break;
        }
    }
}
