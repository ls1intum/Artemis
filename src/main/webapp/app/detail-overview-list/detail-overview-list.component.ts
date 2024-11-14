import { Component, OnDestroy, OnInit, ViewEncapsulation, effect, input, signal } from '@angular/core';
import { isEmpty } from 'lodash-es';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize, TooltipPlacement } from 'app/shared/components/button.component';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { Detail } from 'app/detail-overview-list/detail.model';
import { UMLModel } from '@ls1intum/apollon';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Subscription } from 'rxjs';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { isEqual } from 'lodash-es';

export interface DetailOverviewSection {
    headline: string;
    details: Detail[];
}

export enum DetailType {
    Link = 'detail-link',
    Text = 'detail-text',
    DefaultProfilePicture = 'detail-default-profile-picture',
    Image = 'detail-image',
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
    ProgrammingCheckoutDirectories = 'detail-checkout-directories',
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

    sections = input.required<DetailOverviewSection[]>();

    displayedSections = signal<DetailOverviewSection[]>([]);

    sectionsBeforeChanges = signal<DetailOverviewSection[]>([]);

    // headline list for navigation bar
    headlines: { id: string; translationKey: string }[];
    // headline record to avoid function call in html
    headlinesRecord: Record<string, string>;

    profileSubscription: Subscription;
    isLocalVC = false;

    constructor(
        private modelingExerciseService: ModelingExerciseService,
        private alertService: AlertService,
        private profileService: ProfileService,
    ) {
        effect(
            () => {
                if (!isEqual(this.sections(), this.sectionsBeforeChanges())) {
                    console.log('Sections have changed');
                    this.sections().forEach((section, sectionIndex) => {
                        const previousSection = this.sectionsBeforeChanges()[sectionIndex];
                        if (!isEqual(section, previousSection)) {
                            console.log(`Section ${sectionIndex} has changed:`);
                            section.details.forEach((detail, detailIndex) => {
                                const previousDetail = previousSection.details[detailIndex];
                                if (!isEqual(detail, previousDetail)) {
                                    console.log(`  Detail ${detailIndex} has changed:`);
                                    console.log('    Current:', detail);
                                    console.log('    Previous:', previousDetail);
                                }
                            });
                        }
                    });
                    this.displayedSections.set(this.sections());
                } else {
                    console.log('Sections are the same');
                }
            },
            { allowSignalWrites: true },
        );
    }

    ngOnInit() {
        this.headlines = this.sections().map((section) => {
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

        this.sectionsBeforeChanges.set(this.sections());
        this.displayedSections.set(this.sections());
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
