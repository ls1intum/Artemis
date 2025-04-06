import { Component, Input, OnInit } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercise/exercise.utils';
import dayjs from 'dayjs/esm';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exercise-info',
    templateUrl: './exercise-info.component.html',
    styleUrls: ['../../shared/side-panel/side-panel.scss'],
    imports: [TranslateDirective, NgTemplateOutlet, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class ExerciseInfoComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;

    dueDate?: dayjs.Dayjs;
    individualComplaintDueDate?: dayjs.Dayjs;
    canComplainLaterOn: boolean;

    ngOnInit(): void {
        this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
        if (this.exercise.course?.maxComplaintTimeDays) {
            this.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                this.exercise,
                this.exercise.course.maxComplaintTimeDays,
                this.studentParticipation?.results?.last(),
                this.studentParticipation,
            );
        }
        // The student can either still submit or there is a submission where the student did not have the chance to complain yet
        this.canComplainLaterOn =
            ((this.dueDate && dayjs().isBefore(this.dueDate)) || (!!this.studentParticipation?.submissionCount && !this.individualComplaintDueDate)) &&
            (this.exercise.allowComplaintsForAutomaticAssessments || this.exercise.assessmentType !== AssessmentType.AUTOMATIC);
    }
}
