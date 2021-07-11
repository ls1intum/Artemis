import { Component, EventEmitter, Input, OnChanges, Output, OnInit } from '@angular/core';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
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
    getSubmissionForExercise = ExamParticipationService.getSubmissionForExercise;
    getExerciseButtonTooltip = this.examParticipationService.getExerciseButtonTooltip;

    constructor(protected changeDetectorReference: ChangeDetectorRef, private examParticipationService: ExamParticipationService) {
        super(changeDetectorReference);
    }

    ngOnInit() {
        this.studentExam.exercises?.forEach((exercise) => {
            const item = new ExamExerciseOverviewItem();
            item.exercise = exercise;
            item.icon = 'edit';
            this.examExerciseOverviewItems.push(item);
        });
    }

    ngOnChanges() {
        this.examExerciseOverviewItems?.forEach((item) => {
            this.setExerciseIconStatus(item);
        });
    }

    openExercise(exercise: Exercise) {
        this.onPageChanged.emit({ overViewChange: false, exercise, forceSave: false });
    }

    setExerciseIconStatus(item: ExamExerciseOverviewItem): 'synced' | 'notSynced' {
        const submission = ExamParticipationService.getSubmissionForExercise(item.exercise);
        if (!submission) {
            return 'synced';
        }
        if (submission.submitted) {
            item.icon = 'check';
        }
        if (submission.isSynced) {
            // make status blue
            return 'synced';
        } else {
            // make status yellow
            item.icon = 'edit';
            return 'notSynced';
        }
    }
}
