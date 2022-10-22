import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-tutorial-group-free-period-row-buttons',
    templateUrl: './tutorial-group-free-period-row-buttons.component.html',
})
export class TutorialGroupFreePeriodRowButtonsComponent {
    @Input() course: Course;
    @Input() tutorialGroupConfiguration: TutorialGroupsConfiguration;
    @Input() tutorialFreePeriod: TutorialGroupFreePeriod;

    @Output() tutorialFreePeriodDeleted = new EventEmitter<void>();

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faWrench = faWrench;
    faUsers = faUsers;
    faTimes = faTimes;

    constructor(private tutorialGroupFreePeriodService: TutorialGroupFreePeriodService) {}

    deleteTutorialFreePeriod = () => {
        this.tutorialGroupFreePeriodService.delete(this.course.id!, this.tutorialGroupConfiguration.id!, this.tutorialFreePeriod.id!).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.tutorialFreePeriodDeleted.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    };
}
