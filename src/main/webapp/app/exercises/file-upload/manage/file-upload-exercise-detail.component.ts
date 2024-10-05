import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { SafeHtml } from '@angular/platform-browser';
import { Subscription } from 'rxjs';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { filter } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { ExerciseType } from 'app/entities/exercise.model';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import dayjs from 'dayjs/esm';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/entities/course.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { DetailOverviewSection } from 'app/detail-overview-list/detail-overview-list.component';
import {
    getExerciseGeneralDetailsSection,
    getExerciseGradingDefaultDetails,
    getExerciseGradingInstructionsCriteriaDetails,
    getExerciseMarkdownSolution,
    getExerciseModeDetailSection,
    getExerciseProblemDetailSection,
} from 'app/exercises/shared/utils';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Component({
    selector: 'jhi-file-upload-exercise-detail',
    templateUrl: './file-upload-exercise-detail.component.html',
})
export class FileUploadExerciseDetailComponent implements OnInit, OnDestroy {
    private eventManager = inject(EventManager);
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private statisticsService = inject(StatisticsService);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    readonly documentationType: DocumentationType = 'FileUpload';
    readonly ExerciseType = ExerciseType;
    readonly dayjs = dayjs;

    private subscription: Subscription;
    private eventSubscriber: Subscription;

    fileUploadExercise: FileUploadExercise;
    isExamExercise: boolean;
    course?: Course;
    doughnutStats: ExerciseManagementStatisticsDto;
    exerciseDetailSections: DetailOverviewSection[];
    formattedProblemStatement: SafeHtml | null;
    formattedExampleSolution: SafeHtml | null;
    formattedGradingInstructions: SafeHtml | null;

    /**
     * Initializes subscription for file upload exercise
     */
    ngOnInit() {
        // TODO: route determines whether the component is in exam mode
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['exerciseId']);
        });
        this.registerChangeInFileUploadExercises();
    }

    /**
     * Loads file upload exercise from the server
     * @param exerciseId the id of the file upload exercise
     */
    load(exerciseId: number) {
        // TODO: Use a separate find method for exam exercises containing course, exam, exerciseGroup and exercise id
        this.fileUploadExerciseService
            .find(exerciseId)
            .pipe(filter((res) => !!res.body))
            .subscribe({
                next: (fileUploadExerciseResponse: HttpResponse<FileUploadExercise>) => {
                    this.fileUploadExercise = fileUploadExerciseResponse.body!;
                    this.isExamExercise = this.fileUploadExercise.exerciseGroup !== undefined;
                    this.course = this.isExamExercise ? this.fileUploadExercise.exerciseGroup?.exam?.course : this.fileUploadExercise.course;
                    this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.fileUploadExercise.gradingInstructions);
                    this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.fileUploadExercise.problemStatement);
                    this.formattedExampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.fileUploadExercise.exampleSolution);
                    this.exerciseDetailSections = this.getExerciseDetailSections();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
    }

    getExerciseDetailSections(): DetailOverviewSection[] {
        const exercise = this.fileUploadExercise;
        const generalSection = getExerciseGeneralDetailsSection(exercise);
        const modeSection = getExerciseModeDetailSection(exercise);
        const problemSection = getExerciseProblemDetailSection(this.formattedProblemStatement, this.fileUploadExercise);
        const solutionSection = getExerciseMarkdownSolution(exercise, this.formattedExampleSolution);
        const defaultGradingDetails = getExerciseGradingDefaultDetails(exercise);
        const gradingInstructionsCriteriaDetails = getExerciseGradingInstructionsCriteriaDetails(exercise, this.formattedGradingInstructions);
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
    }

    /**
     * Unsubscribes on component destruction
     */
    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * Listens to file upload exercise list modifications
     */
    registerChangeInFileUploadExercises() {
        this.eventSubscriber = this.eventManager.subscribe('fileUploadExerciseListModification', () => this.load(this.fileUploadExercise.id!));
    }
}
