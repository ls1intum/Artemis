import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, from } from 'rxjs';
import { faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EditTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { takeUntil } from 'rxjs/operators';

@Component({
    selector: 'jhi-tutorial-group-free-period-row-buttons',
    templateUrl: './tutorial-group-free-period-row-buttons.component.html',
})
export class TutorialGroupFreePeriodRowButtonsComponent implements OnDestroy {
    @Input() course: Course;
    @Input() tutorialGroupConfiguration: TutorialGroupsConfiguration;
    @Input() tutorialFreePeriod: TutorialGroupFreePeriod;

    @Output() tutorialFreePeriodDeleted = new EventEmitter<void>();
    @Output() tutorialFreePeriodEdited = new EventEmitter<void>();
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngUnsubscribe = new Subject<void>();

    faWrench = faWrench;
    faUsers = faUsers;
    faTimes = faTimes;

    constructor(private tutorialGroupFreePeriodService: TutorialGroupFreePeriodService, private modalService: NgbModal) {}

    deleteTutorialFreePeriod = () => {
        this.tutorialGroupFreePeriodService
            .delete(this.course.id!, this.tutorialGroupConfiguration.id!, this.tutorialFreePeriod.id!)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    this.tutorialFreePeriodDeleted.emit();
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
    };

    openEditFreeDayDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(EditTutorialGroupFreePeriodComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.tutorialGroupFreePeriod = this.tutorialFreePeriod;
        modalRef.componentInstance.tutorialGroupsConfiguration = this.tutorialGroupConfiguration;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe(() => {
                this.tutorialFreePeriodEdited.emit();
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
