import { Component, OnInit, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamLiveEventType, ExamWideAnnouncementEvent } from 'app/exam/overview/services/exam-participation-live-events.service';
import { faCheckCircle, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import dayjs from 'dayjs/esm';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { OrderedListAction } from 'app/shared/monaco-editor/model/actions/ordered-list.action';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-exam-live-announcement-create-modal',
    templateUrl: './exam-live-announcement-create-modal.component.html',
    styleUrls: ['./exam-live-announcement-create-modal.component.scss'],
    imports: [FormsModule, TranslateDirective, MarkdownEditorMonacoComponent, ExamLiveEventComponent, FaIconComponent],
})
export class ExamLiveAnnouncementCreateModalComponent implements OnInit {
    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private examManagementService = inject(ExamManagementService);

    actions = [new BoldAction(), new ItalicAction(), new UnderlineAction(), new CodeAction(), new CodeBlockAction(), new OrderedListAction(), new OrderedListAction()];

    courseId: number;
    examId: number;

    textContent: string;
    html?: SafeHtml;

    status: 'not_submitted' | 'submitting' | 'submitted' = 'not_submitted';

    announcement?: ExamWideAnnouncementEvent;

    // Icons
    faSpinner = faSpinner;
    faCheckCircle = faCheckCircle;

    ngOnInit(): void {
        const data = this.dialogConfig?.data;
        if (data) {
            if (data.courseId !== undefined) {
                this.courseId = data.courseId;
            }
            if (data.examId !== undefined) {
                this.examId = data.examId;
            }
        }
    }

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
            createdDate: dayjs(),
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            text: textContent,
        };
    }

    /**
     * Closes the modal by dismissing it
     */
    clear() {
        this.dialogRef.close('cancel');
    }
}
