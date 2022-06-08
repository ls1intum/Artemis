import { Component, OnInit } from '@angular/core';
import { TutorEffort } from 'app/entities/tutor-effort.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { PlagiarismAndTutorEffortDirective } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-and-tutor-effort.directive';
import { TranslateService } from '@ngx-translate/core';
import { median } from 'simple-statistics';
import { GraphColors } from 'app/entities/statistics.model';
import { round } from 'app/shared/util/utils';

interface TutorEffortRange {
    minimumTimeSpent: number;
    maximumTimeSpent?: number;
}

@Component({
    selector: 'jhi-text-exercise-tutor-effort-statistics',
    templateUrl: './tutor-effort-statistics.component.html',
    styleUrls: ['./tutor-effort-statistics.component.scss'],
})
export class TutorEffortStatisticsComponent extends PlagiarismAndTutorEffortDirective implements OnInit {
    tutorEfforts: TutorEffort[] = [];
    numberOfSubmissions: number;
    totalTimeSpent: number;
    averageTimeSpent: number;
    currentExerciseId: number;
    currentCourseId: number;
    numberOfTutorsInvolvedInCourse: number;
    effortDistribution: number[];
    yScaleMax = 10;
    medianValue: number;

    showMedianLegend = false;

    // Distance value representing step difference between chartLabel entries, i.e:. 1-10, 10-20
    readonly bucketSize = 10;

    xAxisLabel: string;
    yAxisLabel: string;

    // Icons
    faSync = faSync;

    constructor(
        private textExerciseService: TextExerciseService,
        private textAssessmentService: TextAssessmentService,
        private route: ActivatedRoute,
        private translateService: TranslateService,
        private router: Router,
    ) {
        super();
        this.translateService.onLangChange.subscribe(() => {
            this.translateLabels();
        });
    }

    ngOnInit(): void {
        this.translateLabels();
        this.ngxChartLabels = ['[0-10)', '[10-20)', '[20-30)', '[30-40)', '[40-50)', '[50-60)', '[60-70)', '[70-80)', '[80-90)', '[90-100)', '[100-110)', '[110-120)', '120+'];
        this.ngxColor.domain = Array(13).fill(GraphColors.LIGHT_BLUE);
        this.route.params.subscribe((params) => {
            this.currentExerciseId = Number(params['exerciseId']);
            this.currentCourseId = Number(params['courseId']);
        });
        this.loadTutorEfforts();
    }

    loadTutorEfforts() {
        this.textExerciseService.calculateTutorEffort(this.currentExerciseId, this.currentCourseId).subscribe((tutorEffortResponse: TutorEffort[]) => {
            this.handleTutorEffortResponse(tutorEffortResponse);
        });
        this.loadNumberOfTutorsInvolved();
    }

    /**
     * Handler function to handle input data coming from service call.
     * Separation enables better testing
     * @param tutorEffortData - data to handle
     */
    handleTutorEffortResponse(tutorEffortData: TutorEffort[]) {
        this.tutorEfforts = tutorEffortData;
        if (!this.tutorEfforts) {
            return;
        }
        this.numberOfSubmissions = this.tutorEfforts.reduce((n, { numberOfSubmissionsAssessed }) => n + numberOfSubmissionsAssessed, 0);
        const totalTime = this.tutorEfforts.reduce((n, { totalTimeSpentMinutes }) => n + totalTimeSpentMinutes, 0);
        this.totalTimeSpent = Math.round(totalTime * 10) / 10;
        const avgTemp = this.totalTimeSpent === 0 ? 0 : this.numberOfSubmissions / this.totalTimeSpent;
        this.averageTimeSpent = avgTemp ? Math.round((avgTemp + Number.EPSILON) * 100) / 100 : 0;
        this.distributeEffortToSets();
        this.ngxData = [];
        this.effortDistribution.forEach((effort, index) => {
            this.ngxData.push({ name: this.ngxChartLabels[index], value: effort });
        });
        this.determineMaxChartHeight(this.effortDistribution);
        this.medianValue = this.computeEffortMedian();
        this.highlightMedian(this.medianValue);

        this.ngxData = [...this.ngxData];
    }

    loadNumberOfTutorsInvolved() {
        this.textAssessmentService.getNumberOfTutorsInvolvedInAssessment(this.currentCourseId, this.currentExerciseId).subscribe((response: number) => {
            this.numberOfTutorsInvolvedInCourse = response;
        });
    }

    /**
     * Tutor Effort is distributed among the effortDistribution entries with each entry representing
     * a corresponding index in the chartLables field.
     * chartLabels.["0-10"] - corresponds to effortDistribution[0]
     * chartLabels.["10-20"] - corresponds to effortDistribution[1]
     * and so on. chartlabels is divided in steps of length 10, which is why division/10 and floor function is used.
     */
    distributeEffortToSets() {
        this.effortDistribution = new Array<number>(this.ngxChartLabels.length).fill(0);
        this.tutorEfforts.forEach((effort) => {
            const BUCKET_INDEX = this.determineIndex(effort.totalTimeSpentMinutes);
            this.effortDistribution[BUCKET_INDEX]++;
        });
    }

    /**
     * Handles the click of a user on an arbitrary chart bar.
     * Delegates the user to the assessment dashboard
     */
    onSelect() {
        this.router.navigate(['/course-management', this.currentCourseId, 'assessment-dashboard', this.currentExerciseId]);
    }

    /**
     * Auxiliary method used for tooltip generation
     * @param label the label of the chart bar
     * @returns the median assessed submissions for a bar
     */
    getMedianAmountOfAssessedSubmissions(label: string): number {
        const index = this.ngxChartLabels.indexOf(label);
        const range = this.identifyMinimumAndMaximumTimesSpent(index);

        return this.computeMedianAmountOfAssessedSubmissions(range);
    }

    /**
     * Determines the upper limit for the y-axis
     * @param data the data that should be displayed
     * @private
     */
    private determineMaxChartHeight(data: number[]): void {
        this.yScaleMax = Math.max(this.yScaleMax, ...data);
    }

    /**
     * Auxiliary method that ensures that the chart is instantly translation sensitive
     * @private
     */
    private translateLabels() {
        this.xAxisLabel = this.translateService.instant('artemisApp.textExercise.tutorEffortStatistics.minutes');
        this.yAxisLabel = this.translateService.instant('artemisApp.textExercise.tutorEffortStatistics.tutors');
    }

    /**
     * Auxiliary method that computes and sets the effort median
     * @private
     */
    private computeEffortMedian(): number {
        if (this.tutorEfforts.length === 0) {
            return 0;
        }
        const timeSpent = this.tutorEfforts.map((effort) => effort.totalTimeSpentMinutes);
        return median(timeSpent);
    }

    /**
     * Auxiliary method that determines the Index a tutor effort should be inserted to for statistic calculation
     * @param timeSpent the time the tutor spent
     * @private
     */
    private determineIndex(timeSpent: number): number {
        const BUCKET_LAST_INDEX = this.ngxChartLabels.length - 1;
        const BUCKET_POSITION = timeSpent / this.bucketSize;
        // the element will either be distributed in one of first 12 elements (chartLabels.length)
        // or the last element if the time passed is larger than 120 (i.e.: chartLabels[12] = 120+)
        return Math.min(Math.floor(BUCKET_POSITION), BUCKET_LAST_INDEX);
    }

    /**
     * Sets the color of the bar representing the median to a dark blue in order to highlight it
     * @param medianValue the median amount of time spent for correcting this exercise
     * @private
     */
    private highlightMedian(medianValue: number) {
        const index = this.determineIndex(medianValue);
        if (this.ngxData[index].value > 0) {
            this.ngxColor.domain[index] = GraphColors.BLUE;
            this.showMedianLegend = true;
        } else {
            this.showMedianLegend = false;
        }
    }

    /**
     * Auxiliary method that computes the median assessed submissions for a bucket
     * @param range TutorEffortRange representing the borders for the interval of occupation
     * @returns the median rounded to two decimals
     * @private
     */
    private computeMedianAmountOfAssessedSubmissions(range: TutorEffortRange): number {
        let filterFunction;
        if (range.maximumTimeSpent) {
            filterFunction = (effort: TutorEffort) => effort.totalTimeSpentMinutes >= range.minimumTimeSpent && effort.totalTimeSpentMinutes < range.maximumTimeSpent!;
        } else {
            filterFunction = (effort: TutorEffort) => effort.totalTimeSpentMinutes >= range.minimumTimeSpent;
        }
        const filteredEfforts = this.tutorEfforts.filter(filterFunction);

        return round(median(filteredEfforts.map((effort) => effort.numberOfSubmissionsAssessed)), 2);
    }

    /**
     * Auxiliary method identifying the range of a tutor effort interval
     * @param index computed index based on the position of the bar in the chart
     * @returns TutorEffortRange representing the minimum and maximum border of the interval
     * @private
     */
    private identifyMinimumAndMaximumTimesSpent(index: number): TutorEffortRange {
        const minimumTimesSpentArray = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120];
        const minimumTimeSpent = minimumTimesSpentArray[index];
        const maximumTimeSpent = minimumTimeSpent !== 120 ? minimumTimeSpent + 10 : undefined;

        return { minimumTimeSpent, maximumTimeSpent };
    }
}
