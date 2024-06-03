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
    informationBoxData: InformationBox;

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
        this.totalWorkingTime = this.exam.workingTime;
        this.moduleNumber = this.exam.moduleNumber;
        this.courseName = this.exam.courseName;
        this.examiner = this.exam.examiner;
        this.numberOfExercisesInExam = this.exam.numberOfExercisesInExam;
        this.examinedStudent = this.studentExam.user?.name;
        this.startDate = this.exam.startDate;

        this.informationBoxData = this.prepareInformationBoxData();
    }

    prepareInformationBoxData(): InformationBox {
        const examInformationBoxData: InformationBox = {
            title: this.moduleNumber ?? '',
            content: this.courseName ?? '',
        };
        return examInformationBoxData;
    }
}
