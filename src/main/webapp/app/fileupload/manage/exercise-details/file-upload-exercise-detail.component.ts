import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { FileUploadExerciseService } from '../services/file-upload-exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import dayjs from 'dayjs/esm';
import { onError } from 'app/shared/util/global.utils';
import { EventManager } from 'app/shared/service/event-manager.service';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import {
    getExerciseGeneralDetailsSection,
    getExerciseGradingDefaultDetails,
    getExerciseGradingInstructionsCriteriaDetails,
    getExerciseMarkdownSolution,
    getExerciseModeDetailSection,
    getExerciseProblemDetailSection,
} from 'app/exercise/util/utils';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { map } from 'rxjs/operators';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';

@Component({
    selector: 'jhi-file-upload-exercise-detail',
    templateUrl: './file-upload-exercise-detail.component.html',
    imports: [TranslateDirective, DocumentationButtonComponent, NonProgrammingExerciseDetailCommonActionsComponent, ExerciseDetailStatisticsComponent, DetailOverviewListComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileUploadExerciseDetailComponent {
    private eventManager = inject(EventManager);
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private statisticsService = inject(StatisticsService);
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private destroyRef = inject(DestroyRef);

    readonly documentationType: DocumentationType = 'FileUpload';
    readonly ExerciseType = ExerciseType;
    readonly dayjs = dayjs;

    private exerciseId = toSignal(this.route.params.pipe(map((params) => Number(params['exerciseId']))), { requireSync: true });
    private reloadSignal = signal(0);

    fileUploadExercise = signal<FileUploadExercise | undefined>(undefined);
    doughnutStats = signal<ExerciseManagementStatisticsDto | undefined>(undefined);

    isExamExercise = computed(() => this.fileUploadExercise()?.exerciseGroup !== undefined);

    course = computed(() => {
        const exercise = this.fileUploadExercise();
        if (!exercise) return undefined;
        return this.isExamExercise() ? exercise.exerciseGroup?.exam?.course : exercise.course;
    });

    formattedProblemStatement = computed(() => this.artemisMarkdown.safeHtmlForMarkdown(this.fileUploadExercise()?.problemStatement));
    formattedExampleSolution = computed(() => this.artemisMarkdown.safeHtmlForMarkdown(this.fileUploadExercise()?.exampleSolution));
    formattedGradingInstructions = computed(() => this.artemisMarkdown.safeHtmlForMarkdown(this.fileUploadExercise()?.gradingInstructions));

    exerciseDetailSections = computed(() => {
        const exercise = this.fileUploadExercise();
        if (!exercise) return [];

        const generalSection = getExerciseGeneralDetailsSection(exercise);
        const modeSection = getExerciseModeDetailSection(exercise);
        const problemSection = getExerciseProblemDetailSection(this.formattedProblemStatement(), exercise);
        const solutionSection = getExerciseMarkdownSolution(exercise, this.formattedExampleSolution());
        const defaultGradingDetails = getExerciseGradingDefaultDetails(exercise);
        const gradingInstructionsCriteriaDetails = getExerciseGradingInstructionsCriteriaDetails(exercise, this.formattedGradingInstructions());

        return [
            generalSection,
            modeSection,
            problemSection,
            solutionSection,
            {
                headline: 'artemisApp.exercise.sections.grading',
                details: [...defaultGradingDetails, ...gradingInstructionsCriteriaDetails],
            },
        ];
    });

    constructor() {
        const subscriber = this.eventManager.subscribe('fileUploadExerciseListModification', () => {
            this.reloadSignal.update((n) => n + 1);
        });
        this.destroyRef.onDestroy(() => {
            this.eventManager.destroy(subscriber);
        });

        effect((onCleanup) => {
            const id = this.exerciseId();
            // Track reload signal
            this.reloadSignal();

            if (!id) return;

            const exerciseSub = this.fileUploadExerciseService.find(id).subscribe({
                next: (res) => {
                    if (res.body) {
                        this.fileUploadExercise.set(res.body);
                    }
                },
                error: (error) => {
                    onError(this.alertService, error);
                    this.fileUploadExercise.set(undefined);
                },
            });

            // Flattened logic for stats directly here
            const statsSub = this.statisticsService.getExerciseStatistics(id).subscribe({
                next: (stats) => this.doughnutStats.set(stats),
                error: () => this.doughnutStats.set(undefined),
            });

            onCleanup(() => {
                exerciseSub.unsubscribe();
                statsSub.unsubscribe();
            });
        });
    }
}
