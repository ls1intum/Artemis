import { Component, signal } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TutorialGroupSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';

@Component({
    selector: 'jhi-tutorial-session-create-or-edit-modal',
    imports: [DialogModule, FormsModule, DatePickerModule, InputGroupModule, InputTextModule, ButtonModule],
    templateUrl: './tutorial-session-create-or-edit-modal.component.html',
    styleUrl: './tutorial-session-create-or-edit-modal.component.scss',
})
export class TutorialSessionCreateOrEditModalComponent {
    private session = signal<TutorialGroupSessionDTO | undefined>(undefined);

    isOpen = signal(false);
    date = signal<Date | undefined>(undefined);
    startTime = signal<Date | undefined>(undefined);
    endTime = signal<Date | undefined>(undefined);
    location = signal<string>('');

    open(session?: TutorialGroupSessionDTO) {
        if (session) {
            this.session.set(session);
            this.date.set(session.start.toDate());
            this.startTime.set(session.start.toDate());
            this.endTime.set(session.end.toDate());
            this.location.set(session.location);
        }
        this.isOpen.set(true);
    }
}
