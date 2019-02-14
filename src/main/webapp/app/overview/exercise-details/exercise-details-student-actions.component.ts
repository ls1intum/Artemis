import { Component, HostBinding, Input } from '@angular/core';

@Component({
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../course-overview.scss']
})
export class ExerciseDetailsStudentActionsComponent {
    @Input() kpiTitle: string;
    @Input() kpiValue: string;
    @HostBinding('class.text-center') alignCenter:boolean = true;
}
