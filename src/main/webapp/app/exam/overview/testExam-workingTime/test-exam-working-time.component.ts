import { Component, OnInit, input, signal } from '@angular/core';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { hasTestExamMode } from 'app/exam/shared/entities/exam.model';
import { round } from 'app/foundation/util/utils';
import dayjs from 'dayjs/esm';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-test-exam-working-time',
    templateUrl: './test-exam-working-time.component.html',
    providers: [],
    imports: [ArtemisDurationFromSecondsPipe],
})
export class TestExamWorkingTimeComponent implements OnInit {
    studentExam = input.required<StudentExam>();

    readonly percentUsedWorkingTime = signal(0);
    readonly usedWorkingTime = signal(0);

    /**
     * This component is used to display the used working time and the percentage relative to the default working time for
     * a test exam.
     */
    ngOnInit() {
        if (
            hasTestExamMode(this.studentExam().exam) &&
            this.studentExam().started &&
            this.studentExam().submitted &&
            this.studentExam().workingTime &&
            this.studentExam().startedDate &&
            this.studentExam().submissionDate
        ) {
            const regularExamDuration = this.studentExam().workingTime;
            // As students may submit during the grace period, the workingTime is limited to the regular exam duration
            this.usedWorkingTime.set(Math.min(regularExamDuration!, dayjs(this.studentExam().submissionDate).diff(dayjs(this.studentExam().startedDate), 'seconds')));
            // As students may submit during the grace period, the percentage is limited to 100%
            this.percentUsedWorkingTime.set(Math.min(100, round((this.usedWorkingTime() / regularExamDuration!) * 100, 2)));
        }
    }
}
