import { Component, Input, OnInit } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import dayjs from 'dayjs/esm';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    selector: 'jhi-exercise-info',
    templateUrl: './exercise-info.component.html',
    styleUrls: ['./exercise-info.component.scss'],
    imports: [TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class ExerciseInfoComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;

    dueDate?: dayjs.Dayjs;
    individualComplaintDueDate?: dayjs.Dayjs;
    canComplainLaterOn: boolean;

    ngOnInit(): void {
        this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
        const maxComplaintTimeDays = this.exercise.course?.complaintConfiguration?.maxComplaintTimeDays;
        if (maxComplaintTimeDays) {
            this.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                this.exercise,
                maxComplaintTimeDays,
                getAllResultsOfAllSubmissions(this.studentParticipation?.submissions).last(),
                this.studentParticipation,
            );
        }
        // The student can either still submit or there is a submission where the student did not have the chance to complain yet
        this.canComplainLaterOn =
            ((this.dueDate && dayjs().isBefore(this.dueDate)) || (!!this.studentParticipation?.submissionCount && !this.individualComplaintDueDate)) &&
            (this.exercise.allowComplaintsForAutomaticAssessments || this.exercise.assessmentType !== AssessmentType.AUTOMATIC);
    }
}
