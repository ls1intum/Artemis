import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { ModelingExerciseService, ModelingSubmissionComparisonDTO } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { downloadFile } from 'app/shared/util/download.util';
import { ExportToCsv } from 'export-to-csv';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-plagiarism-inspector',
    styleUrls: ['./plagiarism-inspector.component.scss'],
    templateUrl: './plagiarism-inspector.component.html',
})
export class PlagiarismInspectorComponent implements OnInit {
    /**
     * The modeling exercise for which plagiarism is to be detected.
     */
    exercise: Exercise;

    /**
     * Type of the currently selected exercise.
     */
    exerciseType: ExerciseType;

    /**
     * Results of the plagiarism detection.
     */
    modelingSubmissionComparisons: Array<ModelingSubmissionComparisonDTO>;

    /**
     * Flag to indicate whether the plagiarism detection is currently in progress.
     */
    plagiarismDetectionInProgress: boolean;

    /**
     * Index of the currently selected plagiarism.
     */
    selectedPlagiarismIndex: number;

    /**
     * Subject to be passed into PlagiarismSplitViewComponent to control the split view.
     */
    splitControlSubject: Subject<string> = new Subject<string>();

    constructor(private route: ActivatedRoute, private router: Router, private modelingExerciseService: ModelingExerciseService) {}

    ngOnInit() {
        this.route.data.subscribe(({ exercise }) => {
            this.exercise = exercise;
            this.exerciseType = exercise.type;
        });

        // this.route.params.subscribe((params) => {
        //     this.fetchModelingExercise(params['exerciseId']);
        // });
    }

    /**
     * Handle the 'plagiarismStatusChange' event emitted by PlagiarismHeaderComponent.
     *
     * @param confirmed
     */
    handlePlagiarismStatusChange(confirmed: boolean) {
        this.modelingSubmissionComparisons[this.selectedPlagiarismIndex].confirmed = confirmed;
    }

    /**
     * Handle the 'splitViewChange' event emitted by PlagiarismHeaderComponent.
     *
     * @param pane
     */
    handleSplitViewChange(pane: string) {
        this.splitControlSubject.next(pane);
    }

    /**
     * Trigger the server-side plagiarism detection and fetch its result.
     */
    checkPlagiarism() {
        this.plagiarismDetectionInProgress = true;

        // TODO: Make checkPlagiarism() generic for all exercise types
        // this.modelingExerciseService.checkPlagiarism(this.modelingExercise.id!).subscribe(
        //     (comparisons: Array<ModelingSubmissionComparisonDTO>) => {
        //         this.plagiarismDetectionInProgress = false;
        //         this.modelingSubmissionComparisons = comparisons.sort((c1, c2) => c2.similarity - c1.similarity);
        //     },
        //     () => (this.plagiarismDetectionInProgress = false),
        // );
    }

    /**
     * Download plagiarism detection results as JSON document.
     */
    downloadPlagiarismResultsJson() {
        const json = JSON.stringify(this.modelingSubmissionComparisons);
        const blob = new Blob([json], { type: 'application/json' });

        // TODO
        // downloadFile(blob, `check-plagiarism-modeling-exercise_${this.modelingExercise.id}.json`);
    }

    /**
     * Download plagiarism detection results as CSV document.
     */
    downloadPlagiarismResultsCsv() {
        // TODO
        // if (this.modelingSubmissionComparisons.length > 0) {
        //     const csvExporter = new ExportToCsv({
        //         fieldSeparator: ';',
        //         quoteStrings: '"',
        //         decimalSeparator: 'locale',
        //         showLabels: true,
        //         title: `Plagiarism Check for Modeling Exercise ${this.modelingExercise.id}: ${this.modelingExercise.title}`,
        //         filename: `check-plagiarism-modeling-exercise-${this.modelingExercise.id}-${this.modelingExercise.title}`,
        //         useTextFile: false,
        //         useBom: true,
        //         headers: [
        //             'Similarity',
        //             'Confirmed',
        //             'Participant 1',
        //             'Submission 1',
        //             'Score 1',
        //             'Size 1',
        //             'Link 1',
        //             'Participant 2',
        //             'Submission 2',
        //             'Score 2',
        //             'Size 2',
        //             'Link 2',
        //         ],
        //     });
        //
        //     const courseId = this.modelingExercise.course ? this.modelingExercise.course.id : this.modelingExercise.exerciseGroup?.exam?.course?.id;
        //
        //     const baseUrl = location.origin + '/#/course-management/';
        //
        //     const csvData = this.modelingSubmissionComparisons.map((comparisonResult) => {
        //         return Object.assign({
        //             Similarity: comparisonResult.similarity,
        //             Confirmed: comparisonResult.confirmed ?? '',
        //             'Participant 1': comparisonResult.element1.studentLogin,
        //             'Submission 1': comparisonResult.element1.submissionId,
        //             'Score 1': comparisonResult.element1.score,
        //             'Size 1': comparisonResult.element1.size,
        //             'Link 1':
        //                 baseUrl +
        //                 courseId +
        //                 '/modeling-exercises/' +
        //                 this.modelingExercise.id +
        //                 '/submissions/' +
        //                 comparisonResult.element1.submissionId +
        //                 '/assessment?optimal=false&hideBackButton=true',
        //             'Participant 2': comparisonResult.element2.studentLogin,
        //             'Submission 2': comparisonResult.element2.submissionId,
        //             'Score 2': comparisonResult.element2.score,
        //             'Size 2': comparisonResult.element2.size,
        //             'Link 2':
        //                 baseUrl +
        //                 courseId +
        //                 '/modeling-exercises/' +
        //                 this.modelingExercise.id +
        //                 '/submissions/' +
        //                 comparisonResult.element2.submissionId +
        //                 '/assessment?optimal=false&hideBackButton=true',
        //         });
        //     });
        //
        //     csvExporter.generateCsv(csvData);
        // }
    }
}
