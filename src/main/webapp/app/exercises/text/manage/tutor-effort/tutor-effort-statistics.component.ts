import { Component, OnInit } from '@angular/core';
import { TutorEffort } from 'app/entities/tutor-effort.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { PlagiarismAndTutorEffortDirective } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-and-tutor-effort.directive';
import { TranslateService } from '@ngx-translate/core';

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

    // Distance value representing step difference between chartLabel entries, i.e:. 1-10, 10-20
    bucketSize = 10;

    xAxisLabel = this.translateService.instant('artemisApp.textExercise.tutorEffortStatistics.minutes');
    yAxisLabel = this.translateService.instant('artemisApp.textExercise.tutorEffortStatistics.tutors');

    // Icons
    faSync = faSync;

    constructor(
        private textExerciseService: TextExerciseService,
        private textAssessmentService: TextAssessmentService,
        private route: ActivatedRoute,
        private translateService: TranslateService,
    ) {
        super();
    }

    ngOnInit(): void {
        this.ngxChartLabels = ['0-10', '10-20', '20-30', '30-40', '40-50', '50-60', '60-70', '70-80', '80-90', '90-100', '100-110', '110-120', '120+'];
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
        this.effortDistribution.forEach((effort, index) => {
            this.ngxData.push({ name: this.ngxChartLabels[index], value: effort });
        });
        this.determineMaxChartHeight(this.effortDistribution);
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
            const BUCKET_LAST_INDEX = this.ngxChartLabels.length - 1;
            const BUCKET_POSITION = effort.totalTimeSpentMinutes / this.bucketSize;
            // the element will either be distributed in one of first 12 elements (chartLabels.length)
            // or the last element if the time passed is larger than 120 (i.e.: chartLabels[12] = 120+)
            const BUCKET_INDEX = Math.min(Math.floor(BUCKET_POSITION), BUCKET_LAST_INDEX);
            this.effortDistribution[BUCKET_INDEX]++;
        });
    }

    /**
     * Determines the upper limit for the y axis
     * @param data the data that should be displayed
     * @private
     */
    private determineMaxChartHeight(data: number[]): void {
        this.yScaleMax = Math.max(this.yScaleMax, ...data);
    }
}
