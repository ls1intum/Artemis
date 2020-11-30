import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { downloadFile } from 'app/shared/util/download.util';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';

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
     * Result of the automated plagiarism detection
     */
    plagiarismResult?: TextPlagiarismResult | ModelingPlagiarismResult;

    /**
     * True, if an automated plagiarism detection is running; false otherwise.
     */
    detectionInProgress: boolean;

    /**
     * Index of the currently selected comparison.
     */
    selectedComparisonIndex: number;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private modelingExerciseService: ModelingExerciseService,
        private textExerciseService: TextExerciseService,
    ) {}

    ngOnInit() {
        this.route.data.subscribe(({ exercise }) => {
            this.exercise = exercise;
        });
    }

    checkPlagiarism() {
        if (this.exercise.type === ExerciseType.MODELING) {
            this.checkPlagiarismModeling();
        } else {
            this.checkPlagiarismJPlag();
        }
    }

    selectComparisonAtIndex(index: number) {
        this.selectedComparisonIndex = index;
    }

    /**
     * Trigger the server-side plagiarism detection and fetch its result.
     */
    checkPlagiarismJPlag() {
        this.detectionInProgress = true;

        this.textExerciseService.checkPlagiarismJPlag(this.exercise.id!).subscribe(
            (result: TextPlagiarismResult) => {
                this.detectionInProgress = false;

                this.plagiarismResult = result;
                this.selectedComparisonIndex = 0;
            },
            () => (this.detectionInProgress = false),
        );
    }

    /**
     * Trigger the server-side plagiarism detection and fetch its result.
     */
    checkPlagiarismModeling() {
        this.detectionInProgress = true;

        this.modelingExerciseService.checkPlagiarism(this.exercise.id!).subscribe(
            (result: ModelingPlagiarismResult) => {
                this.detectionInProgress = false;

                this.plagiarismResult = result;
                this.selectedComparisonIndex = 0;
            },
            () => (this.detectionInProgress = false),
        );
    }

    /**
     * Download plagiarism detection results as JSON document.
     */
    downloadPlagiarismResultsJson() {
        const json = JSON.stringify(this.plagiarismResult);
        const blob = new Blob([json], { type: 'application/json' });

        downloadFile(blob, `plagiarism-result_${this.exercise.type}-exercise-${this.exercise.id}.json`);
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
