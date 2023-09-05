import { Component, Input } from '@angular/core';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';

@Component({
    selector: 'jhi-conversation-accept-code-of-conduct-dialog',
    templateUrl: `conversation-accept-code-of-conduct-dialog.component.html`,
    styleUrls: ['./conversation-accept-code-of-conduct-dialog.component.scss'],
})
export class ConversationAcceptCodeOfConductDialogComponent extends AbstractDialogComponent {
    @Input() codeOfConduct: string;

    accept() {
        this.close();
    }
}
