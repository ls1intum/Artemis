import { getSubmissionDueDateInformation } from '../exercise-headers-information/submission-due-date-information';
import { InformationBox, InformationBoxComponent } from 'app/shared-ui/information-box/information-box.component';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';

import { Component, OnInit, computed, input, signal } from '@angular/core';
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
import { Course } from 'app/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { NgTemplateOutlet } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
    styleUrls: ['./header-exercise-page-with-details.component.scss'],
    imports: [
        TumAetUiTooltipDirective,
        InformationBoxComponent,
        NgTemplateOutlet,
        FaIconComponent,
        ExerciseCategoriesComponent,
        TranslateDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
    ],
})
export class HeaderExercisePageWithDetailsComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly getIcon = getIcon;
    readonly getIconTooltip = getIconTooltip;
    readonly dayjs = dayjs;

    readonly exercise = input.required<Exercise>();
    readonly studentParticipation = input<StudentParticipation>();
    readonly title = input<string>();
    readonly exam = input<Exam>();
    readonly course = input<Course>();
    readonly isTestRun = input<boolean>(false);
    readonly submissionPolicy = input<SubmissionPolicy>();

    readonly effectiveCourse = computed(() => this.course() ?? getCourseFromExercise(this.exercise()));

    public readonly exerciseCategories = signal<ExerciseCategory[]>(undefined!);
    public readonly dueDate = signal<dayjs.Dayjs | undefined>(undefined);
    public isBeforeStartDate = false;
    public programmingExercise?: ProgrammingExercise;
    public individualComplaintDueDate?: dayjs.Dayjs;
    public readonly nextRelevantDate = signal<dayjs.Dayjs | undefined>(undefined);
    public readonly nextRelevantDateLabel = signal<string | undefined>(undefined);
    public readonly nextRelevantDateStatusBadge = signal<string | undefined>(undefined);
    readonly pointsInformation = computed<InformationBox>(() => ({
        title: 'artemisApp.courseOverview.exerciseDetails.points',
        content: { type: 'string', value: this.exercise().maxPoints ?? 0 },
    }));
    readonly assessmentTypeInformation = computed<InformationBox>(() => ({
        title: 'artemisApp.courseOverview.exerciseDetails.assessmentType',
        content: { type: 'string', value: this.exercise().assessmentType ?? '' },
        isContentComponent: true,
    }));
    readonly bonusPointsInformation = computed<InformationBox>(() => ({
        title: 'artemisApp.courseOverview.exerciseDetails.bonus',
        content: { type: 'string', value: this.exercise().bonusPoints ?? 0 },
    }));
    readonly scoreInclusionInformation = computed<InformationBox>(() => ({
        title: 'artemisApp.exercise.includedInOverallScore',
        content: { type: 'string', value: this.exercise().includedInOverallScore ?? '' },
        tooltip:
            this.exercise().includedInOverallScore === IncludedInOverallScore.INCLUDED_AS_BONUS
                ? 'artemisApp.exercise.includedAsBonusTooltip'
                : 'artemisApp.exercise.notIncludedTooltip',
        isContentComponent: true,
    }));
    readonly categoriesInformation = computed<InformationBox>(() => ({
        title: 'artemisApp.exercise.categories',
        content: { type: 'categories', value: this.exercise() },
        isContentComponent: true,
    }));
    readonly releaseInformation = computed<InformationBox>(() => ({
        title: 'artemisApp.courseOverview.exerciseList.notReleased',
        content: { type: 'dateTime', value: this.exercise().releaseDate },
        isContentComponent: true,
    }));
    readonly difficultyInformation = computed<InformationBox>(() => ({
        title: 'artemisApp.exercise.difficulty',
        content: { type: 'string', value: this.exercise().difficulty ?? '' },
        isContentComponent: true,
    }));
    readonly nextDateInformation = computed<InformationBox>(() => ({
        title: 'artemisApp.courseOverview.exerciseDetails.' + this.nextRelevantDateLabel(),
        content: { type: 'dateTime', value: this.nextRelevantDate() },
        contentColor: this.nextRelevantDateStatusBadge() === 'bg-state-danger' ? 'danger' : undefined,
        isContentComponent: true,
    }));
    readonly dueDateInformation = computed<InformationBox | undefined>(() => {
        const exam = this.exam();
        if (exam) {
            if (this.isTestRun() || !exam.endDate) {
                return undefined;
            }
            return {
                title: 'artemisApp.exam.endDate',
                content: { type: 'dateTime', value: exam.endDate },
                isContentComponent: true,
            };
        }
        return getSubmissionDueDateInformation(this.dueDate());
    });
    public readonly canComplainLaterOn = signal<boolean>(undefined!);

    readonly numberOfSubmissions = computed<number | undefined>(() => {
        const submissionPolicy = this.submissionPolicy();
        if (submissionPolicy?.active) {
            return countSubmissions(this.studentParticipation());
        }
        return undefined;
    });

    icon?: IconProp;

    // Icons
    faQuestionCircle = faQuestionCircle;

    ngOnInit() {
        const exercise = this.exercise();
        const studentParticipation = this.studentParticipation();
        const course = this.effectiveCourse();
        const exam = this.exam();

        this.exerciseCategories.set(exercise.categories || []);

        if (exercise.type) {
            this.icon = getIcon(exercise.type);
        }

        this.programmingExercise = exercise.type === ExerciseType.PROGRAMMING ? exercise : undefined;

        if (exam) {
            this.determineNextRelevantDateExamMode();
        } else {
            this.dueDate.set(getExerciseDueDate(exercise, studentParticipation));
            this.isBeforeStartDate = exercise.startDate ? exercise.startDate.isAfter(dayjs()) : !!exercise.releaseDate?.isAfter(dayjs());
            if (course?.maxComplaintTimeDays) {
                this.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                    exercise,
                    course.maxComplaintTimeDays,
                    getAllResultsOfAllSubmissions(studentParticipation?.submissions).last(),
                    studentParticipation,
                );
            }
            // There is a submission where the student did not have the chance to complain yet
            this.canComplainLaterOn.set(
                !!studentParticipation?.submissionCount &&
                    !this.individualComplaintDueDate &&
                    (exercise.allowComplaintsForAutomaticAssessments || exercise.assessmentType !== AssessmentType.AUTOMATIC),
            );

            this.determineNextRelevantDateCourseMode();
        }
    }

    /**
     * Determines the next date of the exam cycle. If none exists the latest date in the past is determined
     */
    private determineNextRelevantDateExamMode() {
        const exam = this.exam();
        const possibleDates = [exam?.endDate, exam?.publishResultsDate];
        const possibleDatesLabels = ['endDate', 'publishResultsDate'];

        this.determineNextDate(possibleDates, possibleDatesLabels, dayjs());
    }

    /**
     * Determines the next date of the course exercise cycle. If none exists the latest date in the past is determined
     */
    private determineNextRelevantDateCourseMode() {
        const exercise = this.exercise();
        const possibleDates = [exercise.releaseDate, exercise.startDate, exercise.assessmentDueDate, this.individualComplaintDueDate];
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
        this.nextRelevantDate.set(undefined);
        this.nextRelevantDateLabel.set(undefined);
        this.nextRelevantDateStatusBadge.set(undefined);

        for (let i = 0; i < dates.length; i++) {
            if (dates[i] && now.isBefore(dates[i])) {
                this.nextRelevantDate.set(dates[i]);
                this.nextRelevantDateLabel.set(dateLabels[i]);
                this.nextRelevantDateStatusBadge.set('bg-state-success');
                return;
            }
        }
        if (this.canComplainLaterOn()) {
            return;
        }
        const dueDate = this.dueDate();
        for (let i = dates.length - 1; i >= 0; i--) {
            if (dates[i]) {
                if (dueDate && dueDate.isAfter(dates[i])) {
                    return;
                }

                this.nextRelevantDate.set(dates[i]);
                this.nextRelevantDateLabel.set(dateLabels[i]);
                this.nextRelevantDateStatusBadge.set('bg-state-danger');
                return;
            }
        }
    }
}
