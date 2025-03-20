import { Component, OnDestroy, OnInit, ViewEncapsulation, inject, input } from '@angular/core';
import { isEmpty } from 'lodash-es';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { ModelingExerciseService } from 'app/modeling/manage/modeling-exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { Detail } from 'app/shared/detail-overview-list/detail.model';
import { UMLModel } from '@ls1intum/apollon';
import { Subscription } from 'rxjs';
import { PROFILE_LOCALVC, addPublicFilePrefix } from 'app/app.constants';
import { DetailOverviewNavigationBarComponent } from '../detail-overview-navigation-bar/detail-overview-navigation-bar.component';
import { HelpIconComponent } from '../components/help-icon.component';
import { ProgrammingExerciseInstructionComponent } from '../../programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseLifecycleComponent } from 'app/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { DecimalPipe, NgStyle, NgTemplateOutlet } from '@angular/common';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { TranslateDirective } from '../language/translate.directive';
import { IrisEnabledComponent } from 'app/iris/manage/settings/shared/iris-enabled.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from '../../programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';
import { ExerciseDetailDirective } from './exercise-detail.directive';
import { NoDataComponent } from '../no-data-component';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

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
    imports: [
        DetailOverviewNavigationBarComponent,
        HelpIconComponent,
        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseLifecycleComponent,
        NgTemplateOutlet,
        StructuredGradingInstructionsAssessmentLayoutComponent,
        TranslateDirective,
        IrisEnabledComponent,
        ModelingEditorComponent,
        ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent,
        NgStyle,
        ExerciseDetailDirective,
        NoDataComponent,
        DecimalPipe,
        ArtemisTranslatePipe,
    ],
})
export class DetailOverviewListComponent implements OnInit, OnDestroy {
    protected readonly isEmpty = isEmpty;
    protected readonly DetailType = DetailType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;
    protected readonly CHAT = IrisSubSettingsType.CHAT;

    private readonly modelingExerciseService = inject(ModelingExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);

    sections = input.required<DetailOverviewSection[]>();

    // headline list for navigation bar
    headlines: { id: string; translationKey: string }[];
    // headline record to avoid function call in html
    headlinesRecord: Record<string, string>;

    profileSubscription: Subscription;
    isLocalVC = false;

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

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
