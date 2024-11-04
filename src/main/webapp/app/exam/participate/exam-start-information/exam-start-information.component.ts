import { Component, Input, OnInit } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { InformationBox, InformationBoxComponent, InformationBoxContent } from 'app/shared/information-box/information-box.component';
import { Exam } from 'app/entities/exam/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import dayjs from 'dayjs/esm';
import { SafeHtml } from '@angular/platform-browser';

@Component({
    selector: 'jhi-exam-start-information',
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, InformationBoxComponent, ArtemisExamSharedModule],
    templateUrl: './exam-start-information.component.html',
})
export class ExamStartInformationComponent implements OnInit {
    examInformationBoxData: InformationBox[] = [];

    @Input() exam: Exam;
    @Input() studentExam: StudentExam;
    @Input() formattedStartText?: SafeHtml;

    totalPoints?: number;
    totalWorkingTimeInMinutes?: number;
    moduleNumber?: string;
    courseName?: string;
    examiner?: string;
    numberOfExercisesInExam?: number;
    examinedStudent?: string;
    startDate?: dayjs.Dayjs;
    gracePeriodInMinutes?: number;

    ngOnInit(): void {
        this.totalPoints = this.exam.examMaxPoints;
        this.totalWorkingTimeInMinutes = Math.floor(this.exam.workingTime! / 60);
        this.moduleNumber = this.exam.moduleNumber;
        this.courseName = this.exam.courseName;
        this.examiner = this.exam.examiner;
        this.numberOfExercisesInExam = this.exam.numberOfExercisesInExam;
        this.examinedStudent = this.studentExam.user?.name;
        this.startDate = this.exam.startDate;
        this.gracePeriodInMinutes = Math.floor(this.exam.gracePeriod! / 60);

        this.prepareInformationBoxData();
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
            value: this.studentExam,
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
