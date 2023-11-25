import { Component, Input, OnInit } from '@angular/core';
import { faExclamationTriangle, faEye } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';

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
    Date,
    Boolean,
    Markdown,
    GradingCriteria,
    ProgrammingIrisEnabled,
    ProgrammingRepositoryButtons,
    ProgrammingAuxiliaryRepositoryButtons,
    ProgrammingTestStatus,
    ProgrammingDiffReport,
    ProgrammingProblemStatement,
    ProgrammingTimeline,
    ProgrammingBuildStatistics,
}

@Component({
    selector: 'jhi-detail-overview-list',
    templateUrl: './detail-overview-list.component.html',
    styleUrls: ['./detail-overview-list.component.scss'],
})
export class DetailOverviewListComponent implements OnInit {
    protected readonly DetailType = DetailType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    readonly CHAT = IrisSubSettingsType.CHAT;

    @Input()
    sections: DetailOverviewSection[];

    headlines: { id: string; translationKey: string }[];

    // icons
    faExclamationTriangle = faExclamationTriangle;
    faEye = faEye;

    constructor(private modalService: NgbModal) {}

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

    showGitDiff(gitDiff: ProgrammingExerciseGitDiffReport) {
        const modalRef = this.modalService.open(GitDiffReportModalComponent, { size: 'xl' });
        modalRef.componentInstance.report = gitDiff;
    }
}
