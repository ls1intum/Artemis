import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamExerciseOverviewItem } from 'app/entities/exam-exercise-overview-item.model';
import { ButtonTooltipType, ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { faCheck, faEdit } from '@fortawesome/free-solid-svg-icons';
import { QuizExam } from 'app/entities/quiz-exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Submission } from 'app/entities/submission.model';

@Component({
    selector: 'jhi-exam-exercise-overview-page',
    templateUrl: './exam-exercise-overview-page.component.html',
    styleUrls: ['./exam-exercise-overview-page.scss'],
})
export class ExamExerciseOverviewPageComponent extends ExamPageComponent implements OnInit, OnChanges {
    @Input() studentExam: StudentExam;
    @Input() quizExam?: QuizExam;
    @Output() onPageChanged = new EventEmitter<{ overViewChange: boolean; quizExamChange: boolean; exercise?: Exercise; forceSave: boolean }>();
    getIcon = getIcon;
    getIconTooltip = getIconTooltip;
    faEdit = faEdit;

    readonly ExerciseType = ExerciseType;

    examExerciseOverviewItems: ExamExerciseOverviewItem[] = [];
    quizExamIconTooltip: string;
    quizExamIcon: IconProp;
    quizExamIconStatus: IconProp;

    constructor(
        protected changeDetectorReference: ChangeDetectorRef,
        private examParticipationService: ExamParticipationService,
    ) {
        super(changeDetectorReference);
    }

    ngOnInit() {
        this.studentExam.exercises?.forEach((exercise) => {
            const item = new ExamExerciseOverviewItem();
            item.exercise = exercise;
            item.icon = faEdit;
            this.examExerciseOverviewItems.push(item);
        });
        this.quizExamIconTooltip = getIconTooltip(ExerciseType.QUIZ);
        this.quizExamIcon = getIcon(ExerciseType.QUIZ);
    }

    ngOnChanges() {
        this.examExerciseOverviewItems?.forEach((item) => {
            this.setExerciseIconStatus(item);
        });
    }

    openExercise(exercise: Exercise) {
        this.onPageChanged.emit({ overViewChange: false, quizExamChange: false, exercise, forceSave: false });
    }

    /**
     * Open quiz exam
     */
    openQuizExam() {
        this.onPageChanged.emit({ overViewChange: false, quizExamChange: true, exercise: undefined, forceSave: false });
    }

    getExerciseButtonTooltip(exercise: Exercise): ButtonTooltipType {
        return this.examParticipationService.getExerciseButtonTooltip(exercise);
    }

    /**
     * calculate the exercise status (also see exam-navigation-bar.component.ts --> make sure the logic is consistent)
     * also determines the used icon and its color
     * TODO: we should try to extract a method for the common logic which avoids side effects (i.e. changing this.icon)
     *  this method could e.g. return the sync status and the icon
     *
     * @param item the item for which the exercise status should be calculated
     * @return the sync status of the exercise (whether the corresponding submission is saved on the server or not)
     */
    setExerciseIconStatus(item: ExamExerciseOverviewItem): 'synced' | 'notSynced' {
        // start with a yellow status (edit icon)
        item.icon = faEdit;
        const submission = ExamParticipationService.getSubmissionForExercise(item.exercise);
        if (submission) {
            if (submission.submitted) {
                item.icon = faCheck;
            }
            if (!submission.isSynced) {
                item.icon = faEdit;
            }
        }
        return this.getIconClass(submission);
    }

    setQuizExamIconStatus() {
        // start with a yellow status (edit icon)
        this.quizExamIconStatus = faEdit;
        const submission = this.quizExam?.submission;
        if (submission) {
            if (submission.submitted) {
                this.quizExamIconStatus = faCheck;
            }
            if (!submission.isSynced) {
                this.quizExamIconStatus = faEdit;
            }
        }
        return this.getIconClass(submission);
    }

    getIconClass(submission?: Submission) {
        if (!submission) {
            // in case no participation/submission yet exists -> display synced
            return 'synced';
        }
        if (submission.isSynced) {
            // make status blue
            return 'synced';
        } else {
            // make status yellow
            return 'notSynced';
        }
    }

    getQuizExamButtonTooltip(): ButtonTooltipType {
        return this.examParticipationService.getButtonTooltip(this.quizExam?.submission, ExerciseType.QUIZ);
    }
}
