import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { downloadFile, downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';
import { download, generateCsv, mkConfig } from 'export-to-csv';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { PlagiarismOptions } from 'app/plagiarism/shared/entities/PlagiarismOptions';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { tap } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { faChevronRight, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { Range } from 'app/shared/util/utils';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { Subscription } from 'rxjs';
import { PlagiarismResultDTO, PlagiarismResultStats } from 'app/plagiarism/shared/entities/PlagiarismResultDTO';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FormsModule } from '@angular/forms';
import { PlagiarismSidebarComponent } from '../plagiarism-sidebar/plagiarism-sidebar.component';
import { PlagiarismDetailsComponent } from '../plagiarism-details/plagiarism-details.component';
import { PlagiarismRunDetailsComponent } from '../plagiarism-run-details/plagiarism-run-details.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PlagiarismInspectorService } from 'app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.service';

export type PlagiarismCheckState = {
    state: 'COMPLETED' | 'RUNNING';
    messages: string;
};

@Component({
    selector: 'jhi-plagiarism-inspector',
    styleUrls: ['./plagiarism-inspector.component.scss'],
    templateUrl: './plagiarism-inspector.component.html',
    imports: [
        FaIconComponent,
        TranslateDirective,
        FeatureToggleDirective,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        NgbTooltip,
        FormsModule,
        PlagiarismSidebarComponent,
        PlagiarismDetailsComponent,
        PlagiarismRunDetailsComponent,
        ArtemisTranslatePipe,
    ],
})
export class PlagiarismInspectorComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private textExerciseService = inject(TextExerciseService);
    private websocketService = inject(WebsocketService);
    private translateService = inject(TranslateService);
    private inspectorService = inject(PlagiarismInspectorService);
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private modalService = inject(NgbModal);
    private alertService = inject(AlertService);

    /**
     * The exercise for which plagiarism is to be detected.
     */
    exercise: Exercise;

    /**
     * Result of the automated plagiarism detection
     */
    plagiarismResult?: PlagiarismResult;

    /**
     * Statistics for the automated plagiarism detection result
     */
    plagiarismResultStats?: PlagiarismResultStats;

    /**
     * True, if an automated plagiarism detection is running; false otherwise.
     */
    detectionInProgress = false;

    detectionInProgressMessage = '';

    /**
     * Index of the currently selected comparison.
     */
    selectedComparisonId: number;

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
    similarityThreshold = 90;

    /**
     * Ignore submissions with a score less than `minimumScore` in plagiarism detection.
     */
    minimumScore = 0;

    /**
     * Ignore submissions with a size less than `minimumSize` in plagiarism detection.
     */
    minimumSize = 50;

    /**
     * The minimumScore option is only configurable, if this value is true.
     */
    enableMinimumScore = false;

    /**
     * The minimumSize option is only configurable, if this value is true.
     */
    enableMinimumSize = false;
    /**
     * Comparisons that are currently visible (might differ from the original set as filtering can be applied)
     */
    visibleComparisons?: PlagiarismComparison[];
    chartFilterApplied = false;
    /**
     * Offset of the currently visible comparisons to the original set in order to keep the numbering even if comparisons are filtered
     */
    sidebarOffset = 0;

    /**
     * Whether all plagiarism comparisons should be deleted. If this is true, comparisons with the status "approved" or "denied" will also be deleted
     */
    deleteAllPlagiarismComparisons = false;

    readonly FeatureToggle = FeatureToggle;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faChevronRight = faChevronRight;
    private plagiarismWebsocketSubscription?: Subscription;

    ngOnInit() {
        this.route.data.subscribe(({ exercise }) => {
            this.exercise = exercise;

            this.registerToPlagarismDetectionTopic();
            this.getLatestPlagiarismResult();
        });
    }

    ngOnDestroy(): void {
        this.plagiarismWebsocketSubscription?.unsubscribe();
    }

    /**
     * Registers to the websocket topic of the plagiarism check
     * to get feedback about the progress
     */
    registerToPlagarismDetectionTopic() {
        const topic = this.getPlagarismDetectionTopic();
        this.plagiarismWebsocketSubscription = this.websocketService
            .subscribe<PlagiarismCheckState>(topic)
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
            this.detectionInProgressMessage = this.translateService.instant('artemisApp.plagiarism.fetchingResults');
            this.getLatestPlagiarismResult();
        }
    }

    /**
     * Fetch the latest plagiarism result. There might be no plagiarism result for the given exercise yet.
     */
    getLatestPlagiarismResult() {
        this.detectionInProgress = true;

        switch (this.exercise.type) {
            case ExerciseType.PROGRAMMING: {
                this.programmingExerciseService.getLatestPlagiarismResult(this.exercise.id!).subscribe({
                    next: (result) => this.handlePlagiarismResult(result),
                    error: () => this.handleError(),
                });
                return;
            }
            case ExerciseType.TEXT: {
                this.textExerciseService.getLatestPlagiarismResult(this.exercise.id!).subscribe({
                    next: (result) => this.handlePlagiarismResult(result),
                    error: () => this.handleError(),
                });
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

        if (this.generateJPlagReport) {
            this.checkPlagiarismJPlagReport(options);
        } else {
            this.checkPlagiarismJPlag(options);
        }
    }

    selectComparisonWithID(id: number) {
        this.selectedComparisonId = id;
        this.showRunDetails = false;
    }

    /**
     * This method triggers the plagiarism detection for programming exercises and downloads the zipped report generated by JPlag.
     */
    checkPlagiarismJPlagReport(options?: PlagiarismOptions) {
        this.detectionInProgress = true;

        this.programmingExerciseService.checkPlagiarismJPlagReport(this.exercise.id!, options).subscribe({
            next: (response: HttpResponse<Blob>) => {
                this.detectionInProgress = false;
                downloadZipFileFromResponse(response);
            },
            error: (error: HttpErrorResponse) => {
                // Note: for some reason the alert is not shown, we do it manually with a workaround, because the message (part of the body) is not accessible in the error
                const errorMessage = error.headers.get('x-artemisapp-error');
                if (errorMessage === 'error.notEnoughSubmissions') {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: 'Insufficient amount of valid and long enough submissions available for comparison',
                        disableTranslation: true,
                    });
                }
                this.handleError();
            },
        });
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
            this.textExerciseService.checkPlagiarism(this.exercise.id!, options).subscribe({
                next: (result) => this.handlePlagiarismResult(result),
                error: () => this.handleError(),
            });
        } else {
            this.programmingExerciseService.checkPlagiarism(this.exercise.id!, options).subscribe({
                next: (result) => this.handlePlagiarismResult(result),
                error: () => this.handleError(),
            });
        }
    }

    handleError() {
        this.detectionInProgress = false;
    }

    handlePlagiarismResult(result: PlagiarismResultDTO) {
        this.detectionInProgress = false;

        if (result?.plagiarismResult?.comparisons) {
            this.sortComparisonsForResult(result.plagiarismResult);
            this.showRunDetails = true;
        }

        this.plagiarismResult = result?.plagiarismResult;
        this.plagiarismResultStats = result?.plagiarismResultStats;
        this.visibleComparisons = result?.plagiarismResult?.comparisons;
    }

    sortComparisonsForResult(result: PlagiarismResult) {
        result.comparisons = result.comparisons.sort((a, b) => {
            // if the cases share the same similarity, we sort by the id
            if (b.similarity - a.similarity === 0) {
                return b.id - a.id;
            } else {
                return b.similarity - a.similarity;
            }
        });
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
            const exportOptions = {
                fieldSeparator: ';',
                quoteStrings: true,
                quoteCharacter: '"',
                decimalSeparator: 'locale',
                showLabels: true,
                title: `Plagiarism Check for Exercise ${this.exercise.id}: ${this.exercise.title}`,
                filename: `plagiarism-result_${this.exercise.type}-exercise-${this.exercise.id}`,
                useTextFile: false,
                useBom: true,
                columnHeaders: ['Similarity', 'Status', 'Participant 1', 'Submission 1', 'Score 1', 'Size 1', 'Participant 2', 'Submission 2', 'Score 2', 'Size 2'],
            };

            const rowData = (this.plagiarismResult.comparisons as PlagiarismComparison[]).map((comparison) => {
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

            const combinedOptions = mkConfig(exportOptions);
            const csvData = generateCsv(combinedOptions)(rowData);
            download(combinedOptions)(csvData);
        }
    }

    getMinimumSizeLabel() {
        switch (this.exercise.type) {
            case ExerciseType.PROGRAMMING: {
                return 'artemisApp.plagiarism.minimumTokenCount';
            }
            case ExerciseType.TEXT: {
                return 'artemisApp.plagiarism.minimumSize';
            }
            default: {
                return '';
            }
        }
    }

    /**
     * Return the translation identifier of the minimum size tooltip for the current exercise type.
     */
    getMinimumSizeTooltip() {
        switch (this.exercise.type) {
            case ExerciseType.PROGRAMMING: {
                return 'artemisApp.plagiarism.minimumTokenCountTooltipProgrammingExercise';
            }
            case ExerciseType.TEXT: {
                return 'artemisApp.plagiarism.minimumSizeTooltipTextExercise';
            }
            default: {
                return '';
            }
        }
    }

    /**
     * Filters the comparisons visible in {@link PlagiarismSidebarComponent} according to the selected similarity range
     * selected by the user in the chart
     * @param range the range selected by the user in the chart by clicking on a chart bar
     */
    filterByChart(range: Range): void {
        this.visibleComparisons = this.inspectorService.filterComparisons(range, this.plagiarismResult?.comparisons);
        const index = this.plagiarismResult?.comparisons.indexOf(this.visibleComparisons[0]) ?? 0;
        this.sidebarOffset = index !== -1 ? index : 0;
        this.chartFilterApplied = true;
    }

    /**
     * Resets the filter applied by chart interaction
     */
    resetFilter(): void {
        this.visibleComparisons = this.plagiarismResult?.comparisons;
        this.chartFilterApplied = false;
        this.sidebarOffset = 0;
    }

    /**
     * Auxiliary method called if the "Run details" Button is clicked
     * This additional logic is necessary in order to update the {@link PlagiarismRunDetailsComponent#bucketDTOs}
     * @param flag emitted by {@link PlagiarismSidebarComponent#showRunDetailsChange}
     */
    showSimilarityDistribution(flag: boolean): void {
        this.resetFilter();
        this.getLatestPlagiarismResult();
        this.showRunDetails = flag;
    }

    /**
     * Auxiliary method that returns the comparison currently selected by the user
     */
    getSelectedComparison(): PlagiarismComparison {
        // as the id is unique, the filtered array should always have length 1
        return this.visibleComparisons!.filter((comparison) => comparison.id === this.selectedComparisonId)[0];
    }

    /**
     * Switches the state if all plagiarism comparisons should be deleted
     */
    toggleDeleteAllPlagiarismComparisons(): void {
        this.deleteAllPlagiarismComparisons = !this.deleteAllPlagiarismComparisons;
    }

    /**
     * Clean up plagiarism results and related objects belonging to this exercise
     */
    cleanUpPlagiarism(): void {
        this.plagiarismCasesService.cleanUpPlagiarism(this.exercise.id!, this.plagiarismResult!.id!, this.deleteAllPlagiarismComparisons).subscribe({
            next: () => {
                if (this.deleteAllPlagiarismComparisons) {
                    this.deleteAllPlagiarismComparisons = false;
                    this.plagiarismResult = undefined;
                } else {
                    this.deleteAllPlagiarismComparisons = false;
                    this.getLatestPlagiarismResult();
                }
            },
        });
    }

    /**
     * Open a modal that requires the user's confirmation.
     * @param content the modal content
     */
    openCleanUpModal(content: any) {
        this.modalService.open(content).result.then((result: string) => {
            if (result === 'confirm') {
                this.cleanUpPlagiarism();
            }
        });
    }
}
