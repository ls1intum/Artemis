import { Component, Input, OnInit } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { InformationBox, InformationBoxComponent } from 'app/shared/information-box/information-box.component';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-exam-start-information',
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, InformationBoxComponent],
    templateUrl: './exam-start-information.component.html',
    styleUrl: './exam-start-information.component.scss',
})
export class ExamStartInformationComponent implements OnInit {
    examInformationBoxData: InformationBox[] = [];

    @Input() exam: Exam;
    @Input() studentExam: StudentExam;

    totalPoints: number | undefined;
    totalWorkingTime: number | undefined;
    moduleNumber: string | undefined;
    courseName: string | undefined;
    examiner: string | undefined;
    numberOfExercisesInExam: number | undefined;
    examinedStudent: string | undefined;
    startDate: dayjs.Dayjs | undefined;

    ngOnInit(): void {
        this.totalPoints = this.exam.examMaxPoints;
        this.totalWorkingTime = this.exam.workingTime! / 60;
        this.moduleNumber = this.exam.moduleNumber;
        this.courseName = this.exam.courseName;
        this.examiner = this.exam.examiner;
        this.numberOfExercisesInExam = this.exam.numberOfExercisesInExam;
        this.examinedStudent = this.studentExam.user?.name;
        this.startDate = this.exam.startDate;

        this.prepareInformationBoxData();
    }

    prepareEachInformationBox(param1: string, param2: string): InformationBox {
        const examInformationBoxData: InformationBox = {
            title: param1 ?? '',
            content: param2 ?? '',
        };
        return examInformationBoxData;
    }

    prepareInformationBoxData(): void {
        if (this.moduleNumber) {
            const informationBoxModuleNumber = this.prepareEachInformationBox('artemisApp.examManagement.moduleNumber', this.moduleNumber!);
            this.examInformationBoxData.push(informationBoxModuleNumber);
        }
        if (this.courseName) {
            const informationBoxCourseName = this.prepareEachInformationBox('artemisApp.exam.course', this.courseName!);
            this.examInformationBoxData.push(informationBoxCourseName);
        }
        if (this.examiner) {
            const informationBoxExaminer = this.prepareEachInformationBox('artemisApp.examManagement.examiner', this.examiner!);
            this.examInformationBoxData.push(informationBoxExaminer);
        }
        if (this.examinedStudent) {
            const informationBoxExaminedStudent = this.prepareEachInformationBox('artemisApp.exam.examinedStudent', this.examinedStudent!);
            this.examInformationBoxData.push(informationBoxExaminedStudent);
        }
        const informationBoxTotalPoints = this.prepareEachInformationBox('artemisApp.exam.points', this.totalPoints!.toString());
        this.examInformationBoxData.push(informationBoxTotalPoints);

        if (this.numberOfExercisesInExam) {
            const informationBoxNumberOfExercises = this.prepareEachInformationBox('artemisApp.exam.exercises', this.numberOfExercisesInExam!.toString());
            this.examInformationBoxData.push(informationBoxNumberOfExercises);
        }
        if (this.startDate) {
            const informationBoxStartDate = this.prepareEachInformationBox('artemisApp.exam.date', this.startDate.format('YYYY-MM-DD HH:mm'));
            this.examInformationBoxData.push(informationBoxStartDate);
        }
        const informationBoxTotalWorkingTime = this.prepareEachInformationBox('artemisApp.exam.workingTime', this.totalWorkingTime!.toString());
        this.examInformationBoxData.push(informationBoxTotalWorkingTime);
    }
}
