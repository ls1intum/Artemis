import { Component, Input, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { faArrowUpRightFromSquare, faCodeBranch, faCodeCompare, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { isEmpty } from 'lodash-es';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/button.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { Detail } from 'app/detail-overview-list/detail.model';
import { UMLModel } from '@ls1intum/apollon';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Subscription } from 'rxjs';
import { PROFILE_LOCALVC } from 'app/app.constants';

export interface DetailOverviewSection {
    headline: string;
    details: Detail[];
}

export enum DetailType {
    Link = 'detail-link',
    Text = 'detail-text',
    Date = 'detail-date',
    Boolean = 'detail-boolean',
    Markdown = 'detail-markdown',
    GradingCriteria = 'detail-grading-criteria',
    ModelingEditor = 'detail-modeling-editor',
    ProgrammingIrisEnabled = 'detail-iris',
    ProgrammingRepositoryButtons = 'detail-repository-buttons',
    ProgrammingAuxiliaryRepositoryButtons = 'detail-auxiliary-repository-buttons',
    ProgrammingTestStatus = 'detail-test-status',
    ProgrammingDiffReport = 'detail-diff-report',
    ProgrammingProblemStatement = 'detail-problem-statement',
    ProgrammingTimeline = 'detail-timeline',
    ProgrammingBuildStatistics = 'detail-build-statistics',
}

@Component({
    selector: 'jhi-detail-overview-list',
    templateUrl: './detail-overview-list.component.html',
    styleUrls: ['./detail-overview-list.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class DetailOverviewListComponent implements OnInit, OnDestroy {
    protected readonly isEmpty = isEmpty;
    protected readonly DetailType = DetailType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;
    readonly CHAT = IrisSubSettingsType.CHAT;

    @Input()
    sections: DetailOverviewSection[];

    // headline list for navigation bar
    headlines: { id: string; translationKey: string }[];
    // headline record to avoid function call in html
    headlinesRecord: Record<string, string>;

    // icons
    faExclamationTriangle = faExclamationTriangle;
    faCodeCompare = faCodeCompare;
    faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    faCodeBranch = faCodeBranch;

    WARNING = ButtonType.WARNING;

    profileSubscription: Subscription;
    isLocalVC = false;

    constructor(
        private modalService: NgbModal,
        private modelingExerciseService: ModelingExerciseService,
        private alertService: AlertService,
        private profileService: ProfileService,
    ) {}

    ngOnInit() {
        this.headlines = this.sections.map((section) => {
            return {
                id: section.headline.replaceAll('.', '-'),
                translationKey: section.headline,
            };
        });
        this.profileSubscription = this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.isLocalVC = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
        });
        this.headlinesRecord = this.headlines.reduce((previousValue, currentValue) => {
            return { ...previousValue, [currentValue.translationKey]: currentValue.id };
        }, {});
    }

    showGitDiff(gitDiff?: ProgrammingExerciseGitDiffReport) {
        if (!gitDiff) {
            return;
        }

        const modalRef = this.modalService.open(GitDiffReportModalComponent, { windowClass: 'diff-view-modal' });
        modalRef.componentInstance.report = gitDiff;
    }

    downloadApollonDiagramAsPDf(umlModel?: UMLModel, title?: string) {
        if (umlModel) {
            this.modelingExerciseService.convertToPdf(JSON.stringify(umlModel), `${title}-example-solution`).subscribe({
                error: () => {
                    this.alertService.error('artemisApp.modelingExercise.apollonConversion.error');
                },
            });
        }
    }

    ngOnDestroy() {
        this.profileSubscription?.unsubscribe();
    }

    protected readonly TooltipPlacement = TooltipPlacement;
}
