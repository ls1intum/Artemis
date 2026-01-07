import { ChangeDetectionStrategy, Component, OnDestroy, inject, input, output, viewChild } from '@angular/core';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { EditTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { takeUntil } from 'rxjs/operators';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';

@Component({
    selector: 'jhi-tutorial-group-free-period-row-buttons',
    templateUrl: './tutorial-group-free-period-row-buttons.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, DeleteButtonDirective, ArtemisDatePipe, EditTutorialGroupFreePeriodComponent],
})
export class TutorialGroupFreePeriodRowButtonsComponent implements OnDestroy {
    protected readonly faWrench = faWrench;
    protected readonly faUsers = faUsers;
    protected readonly faTrash = faTrash;

    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);

    course = input.required<Course>();
    tutorialGroupConfiguration = input.required<TutorialGroupsConfiguration>();
    tutorialFreePeriod = input.required<TutorialGroupFreePeriod>();

    readonly editFreePeriodDialog = viewChild<EditTutorialGroupFreePeriodComponent>('editFreePeriodDialog');

    readonly tutorialFreePeriodDeleted = output<void>();
    readonly tutorialFreePeriodEdited = output<void>();
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngUnsubscribe = new Subject<void>();

    deleteTutorialFreePeriod = () => {
        this.tutorialGroupFreePeriodService
            .delete(this.course().id!, this.tutorialGroupConfiguration().id!, this.tutorialFreePeriod().id!)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    this.tutorialFreePeriodDeleted.emit();
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
    };

    openEditFreePeriodDialog(event: MouseEvent) {
        event.stopPropagation();
        this.editFreePeriodDialog()?.open();
    }

    onFreePeriodUpdated(): void {
        this.tutorialFreePeriodEdited.emit();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.dialogErrorSource.unsubscribe();
    }
}
