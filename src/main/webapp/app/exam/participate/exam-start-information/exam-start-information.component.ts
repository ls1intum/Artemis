import { Component, Input, OnInit } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { InformationBox, InformationBoxComponent } from 'app/shared/information-box/information-box.component';
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

    buildInformationBox(boxTitle: string, boxContent: string | number, boxContentComponent?: string): InformationBox {
        const examInformationBoxData: InformationBox = {
            title: boxTitle ?? '',
            content: boxContent ?? '',
            contentComponent: boxContentComponent,
        };
        return examInformationBoxData;
    }

    prepareInformationBoxData(): void {
        if (this.moduleNumber) {
            const informationBoxModuleNumber = this.buildInformationBox('artemisApp.exam.moduleNumber', this.moduleNumber!);
            this.examInformationBoxData.push(informationBoxModuleNumber);
        }
        if (this.courseName) {
            const informationBoxCourseName = this.buildInformationBox('artemisApp.exam.course', this.courseName!);
            this.examInformationBoxData.push(informationBoxCourseName);
        }
        if (this.examiner) {
            const informationBoxExaminer = this.buildInformationBox('artemisApp.examManagement.examiner', this.examiner!);
            this.examInformationBoxData.push(informationBoxExaminer);
        }
        if (this.examinedStudent) {
            const informationBoxExaminedStudent = this.buildInformationBox('artemisApp.exam.examinedStudent', this.examinedStudent!);
            this.examInformationBoxData.push(informationBoxExaminedStudent);
        }
        if (this.startDate) {
            const informationBoxStartDate = this.buildInformationBox('artemisApp.exam.date', this.startDate.toString(), 'formatedDate');
            this.examInformationBoxData.push(informationBoxStartDate);
        }

        const informationBoxTotalWorkingTime = this.buildInformationBox('artemisApp.exam.workingTime', this.exam.workingTime!, 'workingTime');
        this.examInformationBoxData.push(informationBoxTotalWorkingTime);

        const informationBoxTotalPoints = this.buildInformationBox('artemisApp.exam.points', this.totalPoints!.toString());
        this.examInformationBoxData.push(informationBoxTotalPoints);

        if (this.numberOfExercisesInExam) {
            const informationBoxNumberOfExercises = this.buildInformationBox('artemisApp.exam.exercises', this.numberOfExercisesInExam!.toString());
            this.examInformationBoxData.push(informationBoxNumberOfExercises);
        }
    }
}
