import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { downloadFile, downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { PlagiarismResult } from 'app/exercises/shared/plagiarism/types/PlagiarismResult';
import { ExportToCsv } from 'export-to-csv';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { PlagiarismOptions } from 'app/exercises/shared/plagiarism/types/PlagiarismOptions';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { tap } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { faChevronRight, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

export type PlagiarismCheckState = {
    state: 'COMPLETED' | 'RUNNING';
    messages: string;
};

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
    detectionInProgress = false;

    detectionInProgressMessage = '';

    /**
     * Index of the currently selected comparison.
     */
    selectedComparisonIndex: number;

    /**
     * True, if the plagiarism details tab is active.
     */
    showRunDetails = false;

    /**
     * True, if the plagiarism options should be displayed.
     */
    showOptions = false;

    /**
     * If true, the plagiarism detection will return the generated jplag report.
     */
    generateJPlagReport = false;

    /**
     * Minimum similarity (%) of the comparisons to return.
     */
    similarityThreshold = 50;

    /**
     * Ignore submissions with a score less than `minimumScore` in plagiarism detection.
     */
    minimumScore = 0;

    /**
     * Ignore submissions with a size less than `minimumSize` in plagiarism detection.
     */
    minimumSize = 0;

    /**
     * The minimumScore option is only configurable, if this value is true.
     */
    enableMinimumScore = false;

    /**
     * The minimumSize option is only configurable, if this value is true.
     */
    enableMinimumSize = false;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faChevronRight = faChevronRight;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private modelingExerciseService: ModelingExerciseService,
        private programmingExerciseService: ProgrammingExerciseService,
        private textExerciseService: TextExerciseService,
        private websocketService: JhiWebsocketService,
        private translateService: TranslateService,
    ) {}

    ngOnInit() {
        this.route.data.subscribe(({ exercise }) => {
            this.exercise = exercise;

            this.registerToPlagarismDetectionTopic();
            this.getLatestPlagiarismResult();
        });
    }

    /**
     * Registers to the websocket topic of the plagiarism check
     * to get feedback abount the progress
     */
    registerToPlagarismDetectionTopic() {
        const topic = this.getPlagarismDetectionTopic();
        this.websocketService.subscribe(topic);
        this.websocketService
            .receive(topic)
            .pipe(tap((plagiarismCheckState: PlagiarismCheckState) => this.handlePlagiarismCheckStateChange(plagiarismCheckState)))
            .subscribe();
    }

    /**
     * Gets the url to the plagiarism detection websocket topic.
     */
    getPlagarismDetectionTopic() {
        let topic = '/topic/';
        switch (this.exercise.type) {
            case ExerciseType.PROGRAMMING:
                topic += 'programming-exercises';
                break;
            case ExerciseType.TEXT:
                topic += 'text-exercises';
                break;
            case ExerciseType.MODELING:
                topic += 'modeling-exercises';
                break;
        }
        return topic + '/' + this.exercise.id + '/plagiarism-check';
    }

    /**
     * Handles the state change by updating the progress state. Fetches latest
     * results once plagiarism detection is done.
     *
     * @param plagiarismCheckState the state plagiarism check
     */
    handlePlagiarismCheckStateChange(plagiarismCheckState: PlagiarismCheckState) {
        const { state, messages } = plagiarismCheckState;
        this.detectionInProgress = state === 'RUNNING';
        this.detectionInProgressMessage = state === 'RUNNING' ? messages : this.translateService.instant('artemisApp.plagiarism.loading');

        if (state === 'COMPLETED') {
            this.detectionInProgressMessage = this.translateService.instant('artemisApp.plagiarism.fetching-results');
            this.getLatestPlagiarismResult();
        }
    }

    /**
     * Fetch the latest plagiarism result. There might be no plagiarism result for the given exercise yet.
     */
    getLatestPlagiarismResult() {
        this.detectionInProgress = true;

        switch (this.exercise.type) {
            case ExerciseType.MODELING: {
                this.modelingExerciseService.getLatestPlagiarismResult(this.exercise.id!).subscribe(
                    (result) => this.handlePlagiarismResult(result),
                    () => (this.detectionInProgress = false),
                );
                return;
            }
            case ExerciseType.PROGRAMMING: {
                this.programmingExerciseService.getLatestPlagiarismResult(this.exercise.id!).subscribe(
                    (result) => this.handlePlagiarismResult(result),
                    () => (this.detectionInProgress = false),
                );
                return;
            }
            case ExerciseType.TEXT: {
                this.textExerciseService.getLatestPlagiarismResult(this.exercise.id!).subscribe(
                    (result) => this.handlePlagiarismResult(result),
                    () => (this.detectionInProgress = false),
                );
                return;
            }
            default: {
                this.detectionInProgress = false;
            }
        }
    }

    checkPlagiarism() {
        const minimumScore = this.enableMinimumScore ? this.minimumScore : 0;
        const minimumSize = this.enableMinimumSize ? this.minimumSize : 0;

        const options = new PlagiarismOptions(this.similarityThreshold, minimumScore, minimumSize);

        if (this.exercise.type === ExerciseType.MODELING) {
            this.checkPlagiarismModeling(options);
        } else if (this.generateJPlagReport) {
            this.checkPlagiarismJPlagReport(options);
        } else {
            this.checkPlagiarismJPlag(options);
        }
    }

    selectComparisonAtIndex(index: number) {
        this.selectedComparisonIndex = index;
        this.showRunDetails = false;
    }

    /**
     * This method triggers the plagiarism detection for programming exercises and downloads the zipped report generated by JPlag.
     */
    checkPlagiarismJPlagReport(options?: PlagiarismOptions) {
        this.detectionInProgress = true;

        this.programmingExerciseService.checkPlagiarismJPlagReport(this.exercise.id!, options).subscribe(
            (response: HttpResponse<Blob>) => {
                this.detectionInProgress = false;
                downloadZipFileFromResponse(response);
            },
            () => {
                this.detectionInProgress = false;
            },
        );
    }

    isProgrammingExercise() {
        return this.exercise?.type === ExerciseType.PROGRAMMING;
    }

    /**
     * Trigger the server-side plagiarism detection and fetch its result.
     */
    checkPlagiarismJPlag(options?: PlagiarismOptions) {
        this.detectionInProgress = true;

        if (this.exercise.type === ExerciseType.TEXT) {
            this.textExerciseService.checkPlagiarism(this.exercise.id!, options).subscribe(
                (result) => this.handlePlagiarismResult(result),
                () => (this.detectionInProgress = false),
            );
        } else {
            this.programmingExerciseService.checkPlagiarism(this.exercise.id!, options).subscribe(
                (result) => this.handlePlagiarismResult(result),
                () => (this.detectionInProgress = false),
            );
        }
    }

    /**
     * Trigger the server-side plagiarism detection and fetch its result.
     */
    checkPlagiarismModeling(options?: PlagiarismOptions) {
        this.detectionInProgress = true;

        this.modelingExerciseService.checkPlagiarism(this.exercise.id!, options).subscribe(
            (result: ModelingPlagiarismResult) => this.handlePlagiarismResult(result),
            () => (this.detectionInProgress = false),
        );
    }

    handlePlagiarismResult(result: ModelingPlagiarismResult | TextPlagiarismResult) {
        this.detectionInProgress = false;

        if (result?.comparisons) {
            this.sortComparisonsForResult(result);
        }

        this.plagiarismResult = result;
        this.selectedComparisonIndex = 0;
    }

    sortComparisonsForResult(result: PlagiarismResult<any>) {
        result.comparisons = result.comparisons.sort((a, b) => b.similarity - a.similarity);
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
        if (this.plagiarismResult && this.plagiarismResult.comparisons.length > 0) {
            const csvExporter = new ExportToCsv({
                fieldSeparator: ';',
                quoteStrings: '"',
                decimalSeparator: 'locale',
                showLabels: true,
                title: `Plagiarism Check for Exercise ${this.exercise.id}: ${this.exercise.title}`,
                filename: `plagiarism-result_${this.exercise.type}-exercise-${this.exercise.id}`,
                useTextFile: false,
                useBom: true,
                headers: ['Similarity', 'Status', 'Participant 1', 'Submission 1', 'Score 1', 'Size 1', 'Participant 2', 'Submission 2', 'Score 2', 'Size 2'],
            });

            const csvData = (this.plagiarismResult.comparisons as PlagiarismComparison<ModelingSubmissionElement | TextSubmissionElement>[]).map((comparison) => {
                return Object.assign({
                    Similarity: comparison.similarity,
                    Status: comparison.status,
                    'Participant 1': comparison.submissionA.studentLogin,
                    'Submission 1': comparison.submissionA.submissionId,
                    'Score 1': comparison.submissionA.score,
                    'Size 1': comparison.submissionA.size,
                    'Participant 2': comparison.submissionB.studentLogin,
                    'Submission 2': comparison.submissionB.submissionId,
                    'Score 2': comparison.submissionB.score,
                    'Size 2': comparison.submissionB.size,
                });
            });

            csvExporter.generateCsv(csvData);
        }
    }

    /**
     * Return the translation identifier of the minimum size tooltip for the current exercise type.
     */
    getMinimumSizeTooltip() {
        const tooltip = 'artemisApp.plagiarism.minimumSizeTooltip';

        switch (this.exercise.type) {
            case ExerciseType.TEXT: {
                return tooltip + 'Text';
            }
            case ExerciseType.MODELING: {
                return tooltip + 'Modeling';
            }
            default: {
                return tooltip;
            }
        }
    }
}
