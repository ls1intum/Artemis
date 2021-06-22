import { Component, EventEmitter, Input, OnChanges, Output, OnInit } from '@angular/core';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { ChangeDetectorRef } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamExerciseOverviewItem } from 'app/entities/exam-exercise-overview-item.model';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { Submission } from 'app/entities/submission.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

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
    getExerciseButtonTooltip = this.examParticipationService.getExerciseButtonTooltip;

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
}
