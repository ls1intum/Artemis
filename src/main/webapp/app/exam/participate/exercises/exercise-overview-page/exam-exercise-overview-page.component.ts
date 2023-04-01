import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { ExamExerciseOverviewItem } from 'app/entities/exam-exercise-overview-item.model';
import { ButtonTooltipType, ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { faCheck, faEdit } from '@fortawesome/free-solid-svg-icons';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { QuizExamSubmission } from 'app/entities/quiz/quiz-exam-submission.model';

@Component({
    selector: 'jhi-exam-exercise-overview-page',
    templateUrl: './exam-exercise-overview-page.component.html',
    styleUrls: ['./exam-exercise-overview-page.scss'],
})
export class ExamExerciseOverviewPageComponent extends ExamPageComponent implements OnInit, OnChanges {
    @Input() exercises: Exercise[];
    @Input() hasQuizExam?: boolean;
    @Input() quizExamTotalPoints: number;
    @Input() quizExamSubmission?: QuizExamSubmission;
    @Output() onPageChanged = new EventEmitter<{ overViewChange: boolean; quizExamChange: boolean; exercise: Exercise | undefined; forceSave: boolean }>();
    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    examExerciseOverviewItems: ExamExerciseOverviewItem[] = [];
    quizExamItem: ExamExerciseOverviewItem;

    readonly QUIZ = ExerciseType.QUIZ;

    constructor(protected changeDetectorReference: ChangeDetectorRef, private examParticipationService: ExamParticipationService) {
        super(changeDetectorReference);
    }

    ngOnInit() {
        this.exercises?.forEach((exercise) => {
            const item = new ExamExerciseOverviewItem();
            item.exercise = exercise;
            item.icon = faEdit;
            this.examExerciseOverviewItems.push(item);
        });
        this.quizExamItem = new ExamExerciseOverviewItem();
    }

    ngOnChanges() {
        this.examExerciseOverviewItems?.forEach((item) => {
            this.setExerciseIconStatus(item);
        });
    }

    openExercise(exercise: Exercise) {
        this.onPageChanged.emit({ overViewChange: false, quizExamChange: false, exercise: exercise, forceSave: false });
    }

    openQuizExam() {
        this.onPageChanged.emit({ overViewChange: false, quizExamChange: true, exercise: undefined, forceSave: false });
    }

    getExerciseButtonTooltip(examExercise: Exercise): ButtonTooltipType {
        return this.examParticipationService.getExerciseButtonTooltip(examExercise);
    }

    getQuizExamButtonTooltip(): ButtonTooltipType {
        const exercise = { type: ExerciseType.QUIZ, studentParticipations: [{ submissions: [this.quizExamSubmission] } as StudentParticipation] } as Exercise;
        return this.examParticipationService.getExerciseButtonTooltip(exercise);
    }

    setQuizExamIconStatus(): 'synced' | 'notSynced' {
        this.quizExamItem.exercise = {
            type: ExerciseType.QUIZ,
            studentParticipations: [{ submissions: [this.quizExamSubmission] } as StudentParticipation],
        } as Exercise;
        return this.setExerciseIconStatus(this.quizExamItem);
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
}
