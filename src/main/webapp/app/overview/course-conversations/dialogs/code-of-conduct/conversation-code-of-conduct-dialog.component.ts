import { Component, Input } from '@angular/core';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';

@Component({
    selector: 'jhi-conversation-code-of-conduct-dialog',
    template: ` <div class="modal-body" [innerHTML]="codeOfConduct | htmlForMarkdown"></div> `,
})
export class ConversationCodeOfConductDialogComponent extends AbstractDialogComponent {
    @Input() codeOfConduct: string;
}
