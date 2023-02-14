import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
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

@Component({
    selector: 'jhi-file-upload-exercise-detail',
    templateUrl: './file-upload-exercise-detail.component.html',
})
export class FileUploadExerciseDetailComponent implements OnInit, OnDestroy {
    documentationType = DocumentationType.FileUpload;

    readonly dayjs = dayjs;
    fileUploadExercise: FileUploadExercise;
    isExamExercise: boolean;
    course: Course | undefined;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    readonly ExerciseType = ExerciseType;
    doughnutStats: ExerciseManagementStatisticsDto;

    constructor(
        private eventManager: EventManager,
        private fileUploadExerciseService: FileUploadExerciseService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private statisticsService: StatisticsService,
    ) {}

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
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        this.statisticsService.getExerciseStatistics(exerciseId).subscribe((statistics: ExerciseManagementStatisticsDto) => {
            this.doughnutStats = statistics;
        });
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
