import { Component, OnInit, input, signal } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import dayjs from 'dayjs/esm';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    selector: 'jhi-exercise-info',
    templateUrl: './exercise-info.component.html',
    styleUrls: ['./exercise-info.component.scss'],
    imports: [TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class ExerciseInfoComponent implements OnInit {
    readonly exercise = input.required<Exercise>();
    readonly studentParticipation = input<StudentParticipation>();

    readonly dueDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly individualComplaintDueDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly canComplainLaterOn = signal<boolean>(undefined!);

    ngOnInit(): void {
        const exercise = this.exercise();
        this.dueDate.set(getExerciseDueDate(exercise, this.studentParticipation()));
        const studentParticipation = this.studentParticipation();
        if (exercise.course?.maxComplaintTimeDays) {
            this.individualComplaintDueDate.set(
                ComplaintService.getIndividualComplaintDueDate(
                    exercise,
                    exercise.course.maxComplaintTimeDays,
                    getAllResultsOfAllSubmissions(studentParticipation?.submissions).last(),
                    studentParticipation,
                ),
            );
        }
        // The student can either still submit or there is a submission where the student did not have the chance to complain yet
        this.canComplainLaterOn.set(
            ((this.dueDate() && dayjs().isBefore(this.dueDate())) || (!!studentParticipation?.submissionCount && !this.individualComplaintDueDate())) &&
                (exercise.allowComplaintsForAutomaticAssessments || exercise.assessmentType !== AssessmentType.AUTOMATIC),
        );
    }
}
