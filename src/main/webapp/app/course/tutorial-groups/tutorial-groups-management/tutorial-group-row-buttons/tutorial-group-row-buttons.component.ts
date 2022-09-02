import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { faWrench, faUsers, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';

@Component({
    selector: 'jhi-tutorial-group-row-buttons',
    templateUrl: './tutorial-group-row-buttons.component.html',
})
export class TutorialGroupRowButtonsComponent {
    @Input() courseId: number;
    @Input() tutorialGroup: TutorialGroup;

    @Output() tutorialGroupDeleted = new EventEmitter<void>();

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faWrench = faWrench;
    faUsers = faUsers;
    faTimes = faTimes;

    public constructor(private tutorialGroupsService: TutorialGroupsService) {}

    deleteTutorialGroup = () => {
        this.tutorialGroupsService.delete(this.tutorialGroup.id!).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.tutorialGroupDeleted.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    };
}
