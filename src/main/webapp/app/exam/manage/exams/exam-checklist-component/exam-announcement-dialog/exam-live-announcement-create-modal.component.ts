import { Component, inject, input, model, signal } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TumAetUiButtonDirective, TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamLiveEventType, ExamWideAnnouncementEvent } from 'app/exam/overview/services/exam-participation-live-events.service';
import { faCheckCircle, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import dayjs from 'dayjs/esm';
import { BoldAction } from 'app/editor/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/editor/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/editor/monaco-editor/model/actions/underline.action';
import { CodeAction } from 'app/editor/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/editor/monaco-editor/model/actions/code-block.action';
import { OrderedListAction } from 'app/editor/monaco-editor/model/actions/ordered-list.action';
import { UnorderedListAction } from 'app/editor/monaco-editor/model/actions/unordered-list.action';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-exam-live-announcement-create-modal',
    templateUrl: './exam-live-announcement-create-modal.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        MarkdownEditorMonacoComponent,
        ExamLiveEventComponent,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiDialogComponent,
    ],
})
export class ExamLiveAnnouncementCreateModalComponent {
    private examManagementService = inject(ExamManagementService);

    actions = [new BoldAction(), new ItalicAction(), new UnderlineAction(), new CodeAction(), new CodeBlockAction(), new OrderedListAction(), new UnorderedListAction()];

    readonly visible = model(true);
    readonly courseId = input.required<number>();
    readonly examId = input.required<number>();

    readonly textContent = signal<string>(undefined!);
    html?: SafeHtml;

    readonly status = signal<'not_submitted' | 'submitting' | 'submitted'>('not_submitted');

    readonly announcement = signal<ExamWideAnnouncementEvent | undefined>(undefined);

    // Icons
    faSpinner = faSpinner;
    faCheckCircle = faCheckCircle;

    submitAnnouncement() {
        this.status.set('submitting');
        this.examManagementService.createAnnouncement(this.courseId(), this.examId(), this.textContent()).subscribe({
            next: (event: ExamWideAnnouncementEvent) => {
                this.status.set('submitted');
                this.announcement.set(event);
            },
            error: () => {
                this.status.set('not_submitted');
            },
        });
    }

    textContentChanged(textContent: string) {
        this.textContent.set(textContent);
        this.announcement.set({
            id: 0,
            createdDate: dayjs(),
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            text: textContent,
        });
    }

    /**
     * Closes the dialog by dismissing it
     */
    clear() {
        this.visible.set(false);
    }
}
