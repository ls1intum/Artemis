import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheckDouble, faExclamationTriangle, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiCardComponent } from '@tumaet/ui-angular';
import { ProgrammingBuildStatusData, ProgrammingRepositoryBuildOverviewDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { CheckoutDirectoriesDto } from 'app/programming/shared/entities/checkout-directories-dto';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/instructor-repo-download/programming-exercise-instructor-repo-download.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { ProgrammingDiffReportDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { NoDataComponent } from 'app/shared-ui/components/no-data/no-data-component';

interface CheckoutEntry {
    label: string;
    path: string;
    auxiliaryName?: string;
}

interface BuildPanel {
    repositoryType: RepositoryType.TEMPLATE | RepositoryType.SOLUTION;
    title: string;
    description: string;
    expectedScore: string;
    status: ProgrammingBuildStatusData;
    checkouts: CheckoutEntry[];
}

@Component({
    selector: 'jhi-programming-repository-build-overview',
    templateUrl: './programming-repository-build-overview.component.html',
    imports: [
        RouterLink,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiCardComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        FeatureToggleDirective,
        FeatureToggleLinkDirective,
        CodeButtonComponent,
        ProgrammingExerciseInstructorRepoDownloadComponent,
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        UpdatingResultComponent,
        ProgrammingExerciseInstructorStatusComponent,
        ProgrammingDiffReportDetailComponent,
        NoDataComponent,
    ],
})
export class ProgrammingRepositoryBuildOverviewComponent {
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);

    readonly detail = input.required<ProgrammingRepositoryBuildOverviewDetail>();
    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly checkoutLoading = signal(false);
    protected readonly checkoutError = signal(false);
    private readonly checkoutDefaults = signal<CheckoutDirectoriesDto | undefined>(undefined);
    protected readonly auxiliaryRepositories = computed(() => (this.detail().data.supportsAuxiliaryRepositories ? (this.detail().data.exercise.auxiliaryRepositories ?? []) : []));
    protected readonly panels = computed<BuildPanel[]>(() => [
        this.buildPanel(RepositoryType.TEMPLATE, this.detail().data.templateStatus),
        this.buildPanel(RepositoryType.SOLUTION, this.detail().data.solutionStatus),
    ]);

    constructor() {
        effect((onCleanup) => {
            const { exercise, localCIEnabled } = this.detail().data;
            this.checkoutDefaults.set(undefined);
            this.checkoutError.set(false);
            if (!localCIEnabled || !exercise.isAtLeastEditor || !exercise.programmingLanguage) {
                this.checkoutLoading.set(false);
                return;
            }
            this.checkoutLoading.set(true);
            const subscription = this.programmingExerciseService
                .getCheckoutDirectoriesForProgrammingLanguage(exercise.programmingLanguage, exercise.buildConfig?.checkoutSolutionRepository ?? false)
                .subscribe({
                    next: (directories) => {
                        this.checkoutDefaults.set(directories);
                        this.checkoutLoading.set(false);
                    },
                    error: () => {
                        this.checkoutError.set(true);
                        this.checkoutLoading.set(false);
                    },
                });
            onCleanup(() => subscription.unsubscribe());
        });
    }

    private buildPanel(repositoryType: RepositoryType.TEMPLATE | RepositoryType.SOLUTION, status: ProgrammingBuildStatusData): BuildPanel {
        const isTemplate = status.type === ProgrammingExerciseParticipationType.TEMPLATE;
        const kind = isTemplate ? 'template' : 'solution';
        const { exercise } = this.detail().data;
        const config = exercise.buildConfig;
        const defaults = isTemplate ? this.checkoutDefaults()?.submissionBuildPlanCheckoutDirectories : this.checkoutDefaults()?.solutionBuildPlanCheckoutDirectories;
        const checkouts: CheckoutEntry[] = [];
        const addCheckout = (label: string, path?: string, auxiliaryName?: string) => {
            if (path) {
                checkouts.push({ label, path: path.startsWith('/') ? path : `/${path}`, auxiliaryName });
            }
        };
        if (defaults || config) {
            addCheckout(
                `artemisApp.programmingExercise.repositoryBuildOverview.${kind}`,
                config?.assignmentCheckoutPath || (isTemplate ? defaults?.exerciseCheckoutDirectory : defaults?.solutionCheckoutDirectory),
            );
            if (isTemplate && config?.checkoutSolutionRepository) {
                addCheckout('artemisApp.programmingExercise.repositoryBuildOverview.solution', config.solutionCheckoutPath || defaults?.solutionCheckoutDirectory);
            }
            addCheckout('artemisApp.programmingExercise.repositoryBuildOverview.tests', config?.testCheckoutPath || defaults?.testCheckoutDirectory);
            for (const repository of this.auxiliaryRepositories()) {
                addCheckout('artemisApp.programmingExercise.repositoryBuildOverview.auxiliary', repository.checkoutDirectory, repository.name);
            }
        }
        return {
            repositoryType,
            status,
            checkouts,
            title: `artemisApp.programmingExercise.repositoryBuildOverview.${kind}`,
            description: `artemisApp.programmingExercise.repositoryBuildOverview.${kind}Description`,
            expectedScore: `artemisApp.programmingExercise.repositoryBuildOverview.${kind}ExpectedScore`,
        };
    }
}
