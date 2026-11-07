import { Component, DestroyRef, OnInit, computed, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-exam-live-announcement-create-modal',
    templateUrl: './exam-live-announcement-create-modal.component.html',
    imports: [
        FormsModule,
        ArtemisDatePipe,
        TranslateDirective,
        ArtemisTranslatePipe,
        MarkdownEditorMonacoComponent,
        ExamLiveEventComponent,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiDialogComponent,
    ],
})
export class ExamLiveAnnouncementCreateModalComponent implements OnInit {
    private examManagementService = inject(ExamManagementService);
    private destroyRef = inject(DestroyRef);

    readonly actions = [new BoldAction(), new ItalicAction(), new UnderlineAction(), new CodeAction(), new CodeBlockAction(), new OrderedListAction(), new UnorderedListAction()];

    readonly visible = model(true);
    readonly courseId = input.required<number>();
    readonly examId = input.required<number>();

    readonly textContent = signal<string>(undefined!);
    html?: SafeHtml;

    readonly status = signal<'not_submitted' | 'submitting' | 'submitted'>('not_submitted');

    readonly pastAnnouncements = signal<ExamWideAnnouncementEvent[]>([]);
    readonly historyLoadingFailed = signal(false);

    readonly announcement = signal<ExamWideAnnouncementEvent | undefined>(undefined);

    readonly sentAnnouncement = computed(() => {
        const announcement = this.announcement();
        return announcement && !this.pastAnnouncements().some((event) => event.id === announcement.id) ? announcement : undefined;
    });
    private historyRequestId = 0;

    // Icons
    faSpinner = faSpinner;
    faCheckCircle = faCheckCircle;

    ngOnInit() {
        this.loadAnnouncements();
    }

    private loadAnnouncements() {
        const requestId = ++this.historyRequestId;
        this.historyLoadingFailed.set(false);
        this.examManagementService
            .getAnnouncements(this.courseId(), this.examId())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (events) => {
                    if (requestId === this.historyRequestId) {
                        this.pastAnnouncements.set(events);
                    }
                },
                error: () => {
                    if (requestId === this.historyRequestId) {
                        this.historyLoadingFailed.set(true);
                    }
                },
            });
    }

    submitAnnouncement() {
        this.status.set('submitting');
        this.examManagementService.createAnnouncement(this.courseId(), this.examId(), this.textContent()).subscribe({
            next: (event: ExamWideAnnouncementEvent) => {
                this.status.set('submitted');
                this.announcement.set(event);
                this.loadAnnouncements();
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
