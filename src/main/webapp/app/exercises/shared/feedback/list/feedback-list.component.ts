import { Component, Input } from '@angular/core';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { resultIsPreliminary } from 'app/exercises/shared/result/result.utils';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';
import { FeedbackItem } from 'app/exercises/shared/result/detail/result-detail.component';
import { Result } from 'app/entities/result.model';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-feedback-list',
    templateUrl: './feedback-list.component.html',
    styleUrls: ['./feedback-list.scss'],
})
export class FeedbackListComponent {
    readonly AssessmentType = AssessmentType;
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly resultIsPreliminary = resultIsPreliminary;

    @Input() exercise?: Exercise;
    @Input() course?: Course;
    @Input() result: Result;

    @Input() loadingFailed: boolean;
    @Input() numberOfNotExecutedTests?: number;
    @Input() filteredFeedbackList: FeedbackItem[];

    @Input() testCaseCount: number;
    @Input() passedTestCaseCount: number;
    @Input() scaFeedbackCount: number;
    @Input() manualFeedbackCount: number;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
}
