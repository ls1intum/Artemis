import { Component, effect, input } from '@angular/core';

import { InformationBox, InformationBoxComponent, InformationBoxContent } from 'app/ui/information-box/information-box.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import dayjs from 'dayjs/esm';
import { SafeHtml } from '@angular/platform-browser';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-exam-start-information',
    imports: [InformationBoxComponent, StudentExamWorkingTimeComponent, ArtemisDatePipe],
    templateUrl: './exam-start-information.component.html',
})
export class ExamStartInformationComponent {
    examInformationBoxData: InformationBox[] = [];

    readonly exam = input<Exam>(undefined!);
    readonly studentExam = input<StudentExam>(undefined!);
    readonly formattedStartText = input<SafeHtml>();

    totalPoints?: number;
    totalWorkingTimeInMinutes?: number;
    moduleNumber?: string;
    courseName?: string;
    examiner?: string;
    numberOfExercisesInExam?: number;
    examinedStudent?: string;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    gracePeriodInMinutes?: number;

    constructor() {
        effect(() => {
            const exam = this.exam();
            const studentExam = this.studentExam();
            if (!exam || !studentExam) {
                return;
            }
            this.totalPoints = exam.examMaxPoints;
            this.totalWorkingTimeInMinutes = exam.workingTime !== undefined ? Math.floor(exam.workingTime / 60) : undefined;
            this.moduleNumber = exam.moduleNumber;
            this.courseName = exam.courseName;
            this.examiner = exam.examiner;
            this.numberOfExercisesInExam = exam.numberOfExercisesInExam;
            this.examinedStudent = studentExam.user?.name;
            this.startDate = exam.startDate;
            this.endDate = exam.endDate;
            this.gracePeriodInMinutes = exam.gracePeriod !== undefined ? Math.floor(exam.gracePeriod / 60) : undefined;
            this.examInformationBoxData = [];
            this.prepareInformationBoxData();
        });
    }

    buildInformationBox(boxTitle: string, boxContent: InformationBoxContent, isContentComponent = false): InformationBox {
        const examInformationBoxData: InformationBox = {
            title: boxTitle ?? '',
            content: boxContent,
            isContentComponent: isContentComponent,
        };
        return examInformationBoxData;
    }

    prepareInformationBoxData(): void {
        if (this.moduleNumber) {
            const boxContentModuleNumber: InformationBoxContent = {
                type: 'string',
                value: this.moduleNumber,
            };
            const informationBoxModuleNumber = this.buildInformationBox('artemisApp.exam.moduleNumber', boxContentModuleNumber);
            this.examInformationBoxData.push(informationBoxModuleNumber);
        }
        if (this.courseName) {
            const boxContentCourseName: InformationBoxContent = {
                type: 'string',
                value: this.courseName,
            };
            const informationBoxCourseName = this.buildInformationBox('artemisApp.exam.course', boxContentCourseName);
            this.examInformationBoxData.push(informationBoxCourseName);
        }
        if (this.examiner) {
            const boxContentExaminer: InformationBoxContent = {
                type: 'string',
                value: this.examiner,
            };
            const informationBoxExaminer = this.buildInformationBox('artemisApp.examManagement.examiner', boxContentExaminer);
            this.examInformationBoxData.push(informationBoxExaminer);
        }
        if (this.examinedStudent) {
            const boxContentExaminedStudent: InformationBoxContent = {
                type: 'string',
                value: this.examinedStudent,
            };
            const informationBoxExaminedStudent = this.buildInformationBox('artemisApp.exam.examinedStudent', boxContentExaminedStudent);
            this.examInformationBoxData.push(informationBoxExaminedStudent);
        }
        if (this.startDate) {
            const boxContentStartDate: InformationBoxContent = {
                type: 'dateTime',
                value: this.startDate,
            };
            const informationBoxStartDate = this.buildInformationBox('artemisApp.exam.date', boxContentStartDate, true);
            this.examInformationBoxData.push(informationBoxStartDate);
        }

        const boxContentExamWorkingTime: InformationBoxContent = {
            type: 'workingTime',
            value: this.studentExam(),
        };

        const informationBoxTotalWorkingTime = this.buildInformationBox('artemisApp.exam.workingTime', boxContentExamWorkingTime, true);
        this.examInformationBoxData.push(informationBoxTotalWorkingTime);
        const boxContentTotalPoints: InformationBoxContent = {
            type: 'string',
            value: this.totalPoints?.toString() ?? '',
        };

        const informationBoxTotalPoints = this.buildInformationBox('artemisApp.exam.points', boxContentTotalPoints);
        this.examInformationBoxData.push(informationBoxTotalPoints);

        if (this.numberOfExercisesInExam) {
            const boxContent: InformationBoxContent = {
                type: 'string',
                value: this.numberOfExercisesInExam?.toString(),
            };
            const informationBoxNumberOfExercises = this.buildInformationBox('artemisApp.exam.exercises', boxContent);
            this.examInformationBoxData.push(informationBoxNumberOfExercises);
        }
    }
}
