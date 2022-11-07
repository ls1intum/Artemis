import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-create-tutorial-group-session',
    templateUrl: './create-tutorial-group-session.component.html',
})
export class CreateTutorialGroupSessionComponent {
    tutorialGroupSessionToCreate: TutorialGroupSessionDTO = new TutorialGroupSessionDTO();
    isLoading: boolean;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    course: Course;

    isInitialized = false;

    constructor(private activeModal: NgbActiveModal, private tutorialGroupSessionService: TutorialGroupSessionService, private alertService: AlertService) {}

    initialize() {
        if (!this.course || !this.tutorialGroup) {
            console.error('Error: Component not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

    createTutorialGroupSession(formData: TutorialGroupSessionFormData) {
        const { date, startTime, endTime, location } = formData;

        this.tutorialGroupSessionToCreate.date = date;
        this.tutorialGroupSessionToCreate.startTime = startTime;
        this.tutorialGroupSessionToCreate.endTime = endTime;
        this.tutorialGroupSessionToCreate.location = location;

        this.isLoading = true;

        this.tutorialGroupSessionService
            .create(this.course.id!, this.tutorialGroup.id!, this.tutorialGroupSessionToCreate)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.activeModal.close();
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.clear();
                },
            });
    }

    clear() {
        this.activeModal.dismiss();
    }
}
