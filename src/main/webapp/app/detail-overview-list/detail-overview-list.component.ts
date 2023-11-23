import { Component, Input, OnInit } from '@angular/core';
import { faExclamationTriangle, faEye } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/button.component';

export interface DetailOverviewSection {
    headline: string;
    details: Detail[];
}

interface Detail {
    type: DetailType;
    title: string;
    titleTranslationProps?: Record<string, string>;
    titleHelpText?: string;
    data: any;
}

export enum DetailType {
    Link,
    Text,
    Boolean,
    Markdown,
    ProgrammingRepositoryButtons,
    ProgrammingAuxiliaryRepositoryButtons,
    ProgrammingTestStatus,
    ProgrammingDiffReport,
    ProgrammingProblemStatement,
    ProgrammingTimeline,
    ProgrammingGradingCriteria,
    ProgrammingBuildStatistics,
}

@Component({
    selector: 'jhi-detail-overview-list',
    templateUrl: './detail-overview-list.component.html',
})
export class DetailOverviewListComponent implements OnInit {
    protected readonly DetailType = DetailType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;

    @Input()
    sections: DetailOverviewSection[];

    headlines: { id: string; translationKey: string }[];

    // icons
    faExclamationTriangle = faExclamationTriangle;
    faEye = faEye;

    ngOnInit() {
        this.headlines = this.sections.map((section) => {
            return {
                id: section.headline.replaceAll('.', '-'),
                translationKey: section.headline,
            };
        });
    }

    getHeadlineId(headlineTranslationKey: string) {
        return this.headlines.find((headline) => headline.translationKey === headlineTranslationKey)!.id;
    }
}
