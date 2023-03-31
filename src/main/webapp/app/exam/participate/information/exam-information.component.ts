import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { endTime, getAdditionalWorkingTime, isExamOverMultipleDays, normalWorkingTime } from 'app/exam/participate/exam.utils';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-exam-information',
    templateUrl: './exam-information.component.html',
})
export class ExamInformationComponent implements OnInit, OnChanges {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;

    examEndDate?: dayjs.Dayjs;
    normalWorkingTime?: number;
    additionalWorkingTime?: number;
    isExamOverMultipleDays: boolean;
    isTestExam?: boolean;
    currentDate?: dayjs.Dayjs;
    maxPoints?: number;
    totalExercises?: number;

    ngOnInit(): void {
        this.examEndDate = endTime(this.exam, this.studentExam);
        this.normalWorkingTime = normalWorkingTime(this.exam);
        this.additionalWorkingTime = getAdditionalWorkingTime(this.exam, this.studentExam);
        this.isExamOverMultipleDays = isExamOverMultipleDays(this.exam, this.studentExam);
        this.isTestExam = this.exam?.testExam;
        this.maxPoints = this.getMaxPoints();
        this.totalExercises = this.getTotalExercises();
        if (this.isTestExam) {
            this.currentDate = dayjs();
        }
    }

    ngOnChanges(): void {
        this.maxPoints = this.getMaxPoints();
        this.totalExercises = this.getTotalExercises();
    }

    private getMaxPoints(): number {
        return (this.exam?.examMaxPoints ?? 0) + (this.studentExam?.quizQuestionTotalPoints ?? 0);
    }

    private getTotalExercises(): number {
        return (this.exam?.numberOfExercisesInExam ?? 0) + (this.studentExam?.quizQuestionTotalPoints ?? 0 > 0 ? 1 : 0);
    }
}
