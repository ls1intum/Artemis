import { Component, Input, OnInit } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-exam-start-information',
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    templateUrl: './exam-start-information.component.html',
    styleUrl: './exam-start-information.component.scss',
})
export class ExamStartInformationComponent implements OnInit {
    @Input() exam: Exam;

    totalPoints: number | undefined;
    totalWorkingTime: number | undefined;

    ngOnInit(): void {
        this.totalPoints = this.exam.examMaxPoints;
        this.totalWorkingTime = this.exam.workingTime;
    }
}
