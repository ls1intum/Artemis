import { Component, Input } from '@angular/core';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';

@Component({
    selector: 'jhi-conversation-code-of-conduct-dialog',
    templateUrl: './conversation-code-of-conduct-dialog.component.html',
})
export class ConversationCodeOfConductDialogComponent extends AbstractDialogComponent {
    @Input() codeOfConduct: string;

    clear() {
        this.close();
    }
}
