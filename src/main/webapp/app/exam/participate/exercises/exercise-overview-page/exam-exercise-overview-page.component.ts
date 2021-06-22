import { Component, EventEmitter, Input, OnChanges, Output, OnInit } from '@angular/core';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { ChangeDetectorRef } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamExerciseOverviewItem } from 'app/entities/exam-exercise-overview-item.model';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';

@Component({
    selector: 'jhi-exam-exercise-overview-page',
    templateUrl: './exam-exercise-overview-page.component.html',
    styleUrls: ['./exam-exercise-overview-page.scss'],
})
export class ExamExerciseOverviewPageComponent extends ExamPageComponent implements OnInit, OnChanges {
    @Input() studentExam: StudentExam;
    @Output() onPageChanged = new EventEmitter<{ overViewChange: boolean; exercise: Exercise; forceSave: boolean }>();
    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    examExerciseOverviewItems: ExamExerciseOverviewItem[] = [];
    getSubmissionForExercise = this.examParticipationService.getSubmissionForExercise;

    constructor(protected changeDetectorReference: ChangeDetectorRef, private examParticipationService: ExamParticipationService) {
        super(changeDetectorReference);
    }

    ngOnInit() {
        this.studentExam.exercises?.forEach((exercise) => {
            console.log('status:', exercise, exercise.studentParticipations?.[0].submissions?.[0].isSynced);
            const item = new ExamExerciseOverviewItem();
            item.exercise = exercise;
            item.submission = this.getSubmissionForExercise(exercise);
            item.icon = 'edit';
            this.examExerciseOverviewItems.push(item);
        });
    }

    ngOnChanges() {
        this.examExerciseOverviewItems?.forEach((item) => {
            console.log('status:', item, item.submission!.isSynced);
            this.setExerciseButtonStatus(item);
        });
    }

    openExercise(exercise: Exercise) {
        this.onPageChanged.emit({ overViewChange: false, exercise, forceSave: false });
    }

    setExerciseButtonStatus(item: ExamExerciseOverviewItem): 'synced' | 'synced active' | 'notSynced' {
        item.icon = 'edit';
        if (item.submission) {
            if (item.submission.submitted) {
                item.icon = 'check';
            }
            if (item.submission.isSynced) {
                // make button blue
                return 'synced';
            } else {
                // make button yellow
                item.icon = 'edit';
                return 'notSynced';
            }
        } else {
            // in case no participation yet exists -> display synced
            return 'synced';
        }
    }

    getExerciseButtonTooltip(item: ExamExerciseOverviewItem): 'submitted' | 'notSubmitted' | 'synced' | 'notSynced' | 'notSavedOrSubmitted' {
        const submission = item.submission;
        if (submission) {
            if (item.exercise!.type === ExerciseType.PROGRAMMING) {
                if (submission.submitted && submission.isSynced) {
                    return 'submitted'; // You have submitted an exercise. You can submit again
                } else if (submission.submitted && !submission.isSynced) {
                    return 'notSavedOrSubmitted'; // You have unsaved and/or unsubmitted changes
                } else if (!submission.submitted && submission.isSynced) {
                    return 'notSubmitted'; // starting point
                } else {
                    return 'notSavedOrSubmitted';
                }
            } else {
                if (submission.isSynced) {
                    return 'synced';
                } else {
                    return 'notSynced';
                }
            }
        } else {
            // submission does not yet exist for this exercise.
            // When the participant navigates to the exercise the submissions are created.
            // Until then show, that the exercise is synced
            return 'synced';
        }
    }
}
