import { Component, Input } from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { Course } from 'app/entities/course.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-edit-tutorial-group-session',
    templateUrl: './edit-tutorial-group-session.component.html',
})
export class EditTutorialGroupSessionComponent {
    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    course: Course;

    @Input()
    tutorialGroupSession: TutorialGroupSession;

    isLoading = false;
    formData?: TutorialGroupSessionFormData = undefined;

    isInitialized = false;

    constructor(private activeModal: NgbActiveModal, private tutorialGroupSessionService: TutorialGroupSessionService, private alertService: AlertService) {}

    initialize() {
        if (!this.tutorialGroupSession || !this.course || !this.tutorialGroup) {
            console.error('Error: Component not fully configured');
        } else {
            this.formData = {
                date: this.tutorialGroupSession.start?.tz(this.course.timeZone).toDate(),
                startTime: this.tutorialGroupSession.start?.tz(this.course.timeZone).format('HH:mm:ss'),
                endTime: this.tutorialGroupSession.end?.tz(this.course.timeZone).format('HH:mm:ss'),
                location: this.tutorialGroupSession.location,
            };
            this.isInitialized = true;
        }
    }

    updateSession(formData: TutorialGroupSessionFormData) {
        const { date, startTime, endTime, location } = formData;

        const tutorialGroupSessionDTO = new TutorialGroupSessionDTO();

        tutorialGroupSessionDTO.date = date;
        tutorialGroupSessionDTO.startTime = startTime;
        tutorialGroupSessionDTO.endTime = endTime;
        tutorialGroupSessionDTO.location = location;

        this.isLoading = true;

        this.tutorialGroupSessionService
            .update(this.course.id!, this.tutorialGroup.id!, this.tutorialGroupSession.id!, tutorialGroupSessionDTO)
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
