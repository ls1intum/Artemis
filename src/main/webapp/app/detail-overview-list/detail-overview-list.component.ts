import { Component, Input, OnInit } from '@angular/core';
import { faExclamationTriangle, faEye } from '@fortawesome/free-solid-svg-icons';
import { isEmpty } from 'lodash-es';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { ParticipationType } from 'app/entities/participation/participation.model';

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
    ModelingEditor,
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
    protected readonly isEmpty = isEmpty;
    protected readonly DetailType = DetailType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ParticipationType = ParticipationType;
    readonly CHAT = IrisSubSettingsType.CHAT;

    @Input()
    sections: DetailOverviewSection[];

    // headline list for navigation bar
    headlines: { id: string; translationKey: string }[];
    // headline record to avoid function call in html
    headlinesRecord: Record<string, string>;

    // icons
    faExclamationTriangle = faExclamationTriangle;
    faEye = faEye;

    constructor(
        private modalService: NgbModal,
        private modelingExerciseService: ModelingExerciseService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        this.headlines = this.sections.map((section) => {
            return {
                id: section.headline.replaceAll('.', '-'),
                translationKey: section.headline,
            };
        });
        this.headlinesRecord = this.headlines.reduce((previousValue, currentValue) => {
            return { ...previousValue, [currentValue.translationKey]: currentValue.id };
        }, {});
    }

    showGitDiff(gitDiff: ProgrammingExerciseGitDiffReport) {
        const modalRef = this.modalService.open(GitDiffReportModalComponent, { size: 'xl' });
        modalRef.componentInstance.report = gitDiff;
    }

    downloadApollonDiagramAsPDf(umlModel: string, title: string) {
        if (umlModel) {
            this.modelingExerciseService.convertToPdf(umlModel, `${title}-example-solution`).subscribe({
                error: () => {
                    this.alertService.error('artemisApp.modelingExercise.apollonConversion.error');
                },
            });
        }
    }
}
