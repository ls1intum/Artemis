import { Component, Input, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import dayjs from 'dayjs/esm';
import { ComplaintService } from 'app/complaints/complaint.service';
import { AssessmentType } from 'app/entities/assessment-type.model';

@Component({
    selector: 'jhi-exercise-info',
    templateUrl: './exercise-info.component.html',
    styleUrls: ['../../../shared/side-panel/side-panel.scss'],
})
export class ExerciseInfoComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;

    dueDate?: dayjs.Dayjs;
    individualComplaintDeadline?: dayjs.Dayjs;
    canComplainLaterOn: boolean;

    ngOnInit(): void {
        this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
        if (this.exercise.course?.maxComplaintTimeDays) {
            this.individualComplaintDeadline = ComplaintService.getIndividualComplaintDueDate(
                this.exercise,
                this.exercise.course.maxComplaintTimeDays,
                this.studentParticipation?.results?.last(),
                this.studentParticipation,
            );
        }
        // The student can either still submit or there is a submission where the student did not have the chance to complain yet
        this.canComplainLaterOn =
            (dayjs().isBefore(this.dueDate) || (!!this.studentParticipation?.submissionCount && !this.individualComplaintDeadline)) &&
            (this.exercise.allowComplaintsForAutomaticAssessments || this.exercise.assessmentType !== AssessmentType.AUTOMATIC);
    }
}
