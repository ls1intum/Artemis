import { Component } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/shared/markdown-editor/commands/italic.command';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';
import { CodeCommand } from 'app/shared/markdown-editor/commands/code.command';
import { CodeBlockCommand } from 'app/shared/markdown-editor/commands/codeblock.command';
import { OrderedListCommand } from 'app/shared/markdown-editor/commands/orderedListCommand';
import { UnorderedListCommand } from 'app/shared/markdown-editor/commands/unorderedListCommand';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamLiveEventType, ExamWideAnnouncementEvent } from 'app/exam/participate/exam-participation-live-events.service';
import { faCheckCircle, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-exam-live-announcement-create-modal',
    templateUrl: './exam-live-announcement-create-modal.component.html',
    styleUrls: ['./exam-live-announcement-create-modal.component.scss'],
})
export class ExamLiveAnnouncementCreateModalComponent {
    COMMANDS = [new BoldCommand(), new ItalicCommand(), new UnderlineCommand(), new CodeCommand(), new CodeBlockCommand(), new OrderedListCommand(), new UnorderedListCommand()];

    courseId: number;
    examId: number;

    textContent: string;
    html?: SafeHtml;

    status: 'not_submitted' | 'submitting' | 'submitted' = 'not_submitted';

    announcement?: ExamWideAnnouncementEvent;

    // Icons
    faSpinner = faSpinner;
    faCheckCircle = faCheckCircle;

    constructor(
        private activeModal: NgbActiveModal,
        private examManagementService: ExamManagementService,
        private accountService: AccountService,
    ) {}

    submitAnnouncement() {
        this.status = 'submitting';
        this.examManagementService.createAnnouncement(this.courseId, this.examId, this.textContent).subscribe({
            next: (event: ExamWideAnnouncementEvent) => {
                this.status = 'submitted';
                this.announcement = event;
            },
            error: () => {
                this.status = 'not_submitted';
            },
        });
    }

    textContentChanged(textContent: string) {
        this.textContent = textContent;
        this.announcement = {
            id: 0,
            createdBy: this.accountService.userIdentity?.name ?? 'John Doe',
            createdDate: dayjs(),
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            text: textContent,
        };
    }

    /**
     * Closes the modal by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
