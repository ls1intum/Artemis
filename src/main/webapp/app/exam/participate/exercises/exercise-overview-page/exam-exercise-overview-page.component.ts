import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamExerciseOverviewItem } from 'app/entities/exam-exercise-overview-item.model';
import { ButtonTooltipType, ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { faCheck, faEdit } from '@fortawesome/free-solid-svg-icons';
import { QuizExam } from 'app/entities/quiz-exam.model';

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

    readonly ExerciseType = ExerciseType;

    examExerciseOverviewItems: ExamExerciseOverviewItem[] = [];
    quizExamIcon = faEdit;

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
     * Get tooltip for quiz exam button
     *
     * @return synced
     */
    getQuizExamButtonTooltip(): ButtonTooltipType {
        return this.examParticipationService.getButtonTooltip(this.quizExam?.submission, ExerciseType.QUIZ);
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
        if (!submission) {
            // in case no participation/submission yet exists -> display synced
            return 'synced';
        }
        if (submission.submitted) {
            item.icon = faCheck;
        }
        if (submission.isSynced) {
            // make status blue
            return 'synced';
        } else {
            // make status yellow
            item.icon = faEdit;
            return 'notSynced';
        }
    }

    /**
     * Set quizExamIcon and return the icon status of the quiz exam
     *
     * @return synced
     */
    setQuizExamIconStatus(): 'synced' | 'notSynced' {
        // start with a yellow status (edit icon)
        this.quizExamIcon = faEdit;
        if (!this.quizExam?.submission) {
            // in case no participation/submission yet exists -> display synced
            return 'synced';
        }
        if (this.quizExam?.submission.submitted) {
            this.quizExamIcon = faCheck;
        }
        if (this.quizExam?.submission.isSynced) {
            // make status blue
            return 'synced';
        } else {
            // make status yellow
            this.quizExamIcon = faEdit;
            return 'notSynced';
        }
    }
}
