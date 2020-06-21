import { Component, OnDestroy, OnInit, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/alert/alert.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import * as moment from 'moment';
import { Subject } from 'rxjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Observable } from 'rxjs/Observable';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-text-editor-exam',
    templateUrl: './text-editor-exam.component.html',
    providers: [ParticipationService],
    styleUrls: ['./text-editor-exam.component.scss'],
})
export class TextEditorExamComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @Input()
    studentParticipation: StudentParticipation;
    @Input()
    exercise: Exercise;

    submission: TextSubmission;

    answer: string;
    private textEditorInput = new Subject<string>();
    textEditorStream$: Observable<TextSubmission>;

    isSaving: boolean;

    constructor(
        private textService: TextEditorService,
        private jhiAlertService: AlertService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private stringCountService: StringCountService,
        private examParticipationService: ExamParticipationService,
    ) {
        this.isSaving = false;
    }

    ngOnInit() {
        if (this.studentParticipation.submissions && this.studentParticipation.submissions.length === 1) {
            this.submission = this.studentParticipation.submissions[0];
        }
        this.textEditorStream$ = this.buildSubmissionStream$();
        this.textEditorStream$.subscribe((textSubmission) => {
            this.examParticipationService.updateSubmission(this.exercise.id, this.studentParticipation.id, textSubmission);
        });
    }

    /**
     * Stream of submissions being emitted on:
     * 1. text editor input after a debounce time of 2 seconds
     */
    private buildSubmissionStream$() {
        const textEditorStream$ = this.textEditorInput
            .asObservable()
            .pipe(debounceTime(2000), distinctUntilChanged())
            .pipe(map((answer: string) => this.submissionForAnswer(answer)));
        return textEditorStream$;
    }

    private submissionForAnswer(answer: string): TextSubmission {
        return { ...this.submission, text: answer, language: this.textService.predictLanguage(answer) };
    }

    ngOnDestroy() {
        if (this.canDeactivate() && this.exercise.id) {
            this.submission.text = this.answer;
            if (this.submission.id) {
                this.examParticipationService.updateSubmission(this.exercise.id, this.studentParticipation.id, this.submission);
            }
        }
    }

    get wordCount(): number {
        return this.stringCountService.countWords(this.answer);
    }

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.answer);
    }

    canDeactivate(): Observable<boolean> | boolean {
        return this.submission.text !== this.answer;
    }

    onTextEditorTab(editor: HTMLTextAreaElement, event: KeyboardEvent) {
        event.preventDefault();
        const value = editor.value;
        const start = editor.selectionStart;
        const end = editor.selectionEnd;

        editor.value = value.substring(0, start) + '\t' + value.substring(end);
        editor.selectionStart = editor.selectionEnd = start + 1;
    }

    onTextEditorInput(event: Event) {
        this.textEditorInput.next((<HTMLTextAreaElement>event.target).value);
    }
}
