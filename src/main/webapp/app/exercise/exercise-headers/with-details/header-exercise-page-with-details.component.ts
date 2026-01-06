import { Component, Input, OnChanges, OnInit, inject } from '@angular/core';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, IncludedInOverallScore, getCourseFromExercise, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { countSubmissions, getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { IncludedInScoreBadgeComponent } from '../included-in-score-badge/included-in-score-badge.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
    styleUrls: ['./header-exercise-page-with-details.component.scss'],
    imports: [
        FaIconComponent,
        NgbTooltip,
        ExerciseCategoriesComponent,
        NgClass,
        IncludedInScoreBadgeComponent,
        TranslateDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
    ],
})
export class HeaderExercisePageWithDetailsComponent implements OnChanges, OnInit {
    private sortService = inject(SortService);

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly getIcon = getIcon;
    readonly getIconTooltip = getIconTooltip;
    readonly dayjs = dayjs;

    @Input() public exercise: Exercise;
    @Input() public studentParticipation?: StudentParticipation;
    @Input() public title: string;
    @Input() public exam?: Exam;
    @Input() public course?: Course;
    @Input() public isTestRun = false;
    @Input() public submissionPolicy?: SubmissionPolicy;

    public exerciseCategories: ExerciseCategory[];
    public dueDate?: dayjs.Dayjs;
    public isBeforeStartDate: boolean;
    public programmingExercise?: ProgrammingExercise;
    public individualComplaintDueDate?: dayjs.Dayjs;
    public nextRelevantDate?: dayjs.Dayjs;
    public nextRelevantDateLabel?: string;
    public nextRelevantDateStatusBadge?: string;
    public dueDateStatusBadge?: string;
    public canComplainLaterOn: boolean;
    public achievedPoints?: number;
    public numberOfSubmissions: number;

    icon: IconProp;

    // Icons
    faQuestionCircle = faQuestionCircle;

    ngOnInit() {
        this.exerciseCategories = this.exercise.categories || [];

        if (this.exercise.type) {
            this.icon = getIcon(this.exercise.type);
        }

        this.programmingExercise = this.exercise.type === ExerciseType.PROGRAMMING ? (this.exercise as ProgrammingExercise) : undefined;

        if (this.exam) {
            this.determineNextRelevantDateExamMode();
        } else {
            this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
            this.isBeforeStartDate = this.exercise.startDate ? this.exercise.startDate.isAfter(dayjs()) : !!this.exercise.releaseDate?.isAfter(dayjs());
            const maxComplaintTimeDays = this.course?.complaintConfiguration?.maxComplaintTimeDays;
            if (maxComplaintTimeDays) {
                this.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                    this.exercise,
                    maxComplaintTimeDays,
                    getAllResultsOfAllSubmissions(this.studentParticipation?.submissions).last(),
                    this.studentParticipation,
                );
            }
            // There is a submission where the student did not have the chance to complain yet
            this.canComplainLaterOn =
                !!this.studentParticipation?.submissionCount &&
                !this.individualComplaintDueDate &&
                (this.exercise.allowComplaintsForAutomaticAssessments || this.exercise.assessmentType !== AssessmentType.AUTOMATIC);

            this.determineNextRelevantDateCourseMode();
        }

        if (this.dueDate) {
            this.dueDateStatusBadge = dayjs().isBefore(this.dueDate) ? 'bg-success' : 'bg-danger';
        }
    }

    ngOnChanges() {
        this.course = this.course ?? getCourseFromExercise(this.exercise);

        if (this.submissionPolicy?.active) {
            this.countSubmissions();
        }
        const results = getAllResultsOfAllSubmissions(this.studentParticipation?.submissions);
        if (results?.length) {
            // The updated participation by the websocket is not guaranteed to be sorted, find the newest result (highest id)
            this.sortService.sortByProperty(results, 'id', false);

            const latestRatedResult = results.filter((result) => result.rated).first();
            if (latestRatedResult) {
                this.achievedPoints = roundValueSpecifiedByCourseSettings((latestRatedResult.score! * this.exercise.maxPoints!) / 100, this.course);
            }
        }
    }

    /**
     * Determines the next date of the exam cycle. If none exists the latest date in the past is determined
     */
    private determineNextRelevantDateExamMode() {
        const possibleDates = [this.exam?.endDate, this.exam?.publishResultsDate];
        const possibleDatesLabels = ['endDate', 'publishResultsDate'];

        this.determineNextDate(possibleDates, possibleDatesLabels, dayjs());
    }

    /**
     * Determines the next date of the course exercise cycle. If none exists the latest date in the past is determined
     */
    private determineNextRelevantDateCourseMode() {
        const possibleDates = [this.exercise.releaseDate, this.exercise.startDate, this.exercise.assessmentDueDate, this.individualComplaintDueDate];
        const possibleDatesLabels = ['releaseDate', 'startDate', 'assessmentDue', 'complaintDue'];

        this.determineNextDate(possibleDates, possibleDatesLabels, dayjs());
    }

    /**
     * Iterates over the given dates and determines the first date that is in the future.
     * If no such date exists, it is determined if the student can complaint later on.
     * If that is also not the case, the latest date in the past is chosen that is after the due date.
     * @param dates that should be iterated over. Can contain undefined if that date does not exist
     * @param dateLabels the labels used to translate the given dates
     * @param now the current date and time
     */
    private determineNextDate(dates: (dayjs.Dayjs | undefined)[], dateLabels: string[], now: dayjs.Dayjs) {
        this.nextRelevantDate = undefined;
        this.nextRelevantDateLabel = undefined;
        this.nextRelevantDateStatusBadge = undefined;

        for (let i = 0; i < dates.length; i++) {
            if (dates[i] && now.isBefore(dates[i])) {
                this.nextRelevantDate = dates[i]!;
                this.nextRelevantDateLabel = dateLabels[i];
                this.nextRelevantDateStatusBadge = 'bg-success';
                return;
            }
        }
        if (this.canComplainLaterOn) {
            return;
        }
        for (let i = dates.length - 1; i >= 0; i--) {
            if (dates[i]) {
                if (this.dueDate && this.dueDate.isAfter(dates[i])) {
                    return;
                }

                this.nextRelevantDate = dates[i]!;
                this.nextRelevantDateLabel = dateLabels[i];
                this.nextRelevantDateStatusBadge = 'bg-danger';
                return;
            }
        }
    }

    private countSubmissions() {
        this.numberOfSubmissions = countSubmissions(this.studentParticipation);
    }
}
