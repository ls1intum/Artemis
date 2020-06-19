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

@Component({
    selector: 'jhi-text-editor-exam',
    templateUrl: './text-editor-exam.component.html',
    providers: [ParticipationService],
    styleUrls: ['./text-editor.component.scss'],
})
export class TextEditorExamComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    textExercise: TextExercise;
    participation: StudentParticipation;
    submission: TextSubmission;
    isSaving: boolean;
    private textEditorInput = new Subject<string>();
    answer: string;

    textEditorStream$: Observable<TextSubmission>;

    @Input()
    participationId: number;

    constructor(
        private textSubmissionService: TextSubmissionService,
        private textService: TextEditorService,
        private jhiAlertService: AlertService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private participationWebsocketService: ParticipationWebsocketService,
        private stringCountService: StringCountService,
        private examParticipationService: ExamParticipationService,
    ) {
        this.isSaving = false;
    }

    ngOnInit() {
        if (Number.isNaN(this.participationId)) {
            return this.jhiAlertService.error('artemisApp.textExercise.error', null, undefined);
        }

        // TODO: replace with new participationExamService
        this.textService.get(this.participationId).subscribe(
            (data: StudentParticipation) => this.updateParticipation(data),
            (error: HttpErrorResponse) => this.onError(error),
        );

        this.textEditorStream$ = this.buildSubmissionStream$();
        this.textEditorStream$.subscribe((textSubmission) => {
            this.examParticipationService.createSubmission(textSubmission, this.participation.exercise.id);
        });
    }

    private updateParticipation(participation: StudentParticipation) {
        this.participation = participation;
        this.textExercise = this.participation.exercise as TextExercise;
        this.textExercise.studentParticipations = [this.participation];
        this.textExercise.participationStatus = participationStatus(this.textExercise);

        if (participation.submissions && participation.submissions.length > 0) {
            this.submission = participation.submissions[0] as TextSubmission;

            if (this.submission && this.submission.text) {
                this.answer = this.submission.text;
            }
        }
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
        if (this.canDeactivate() && this.textExercise.id) {
            let newSubmission = new TextSubmission();
            if (this.submission) {
                newSubmission = this.submission;
            }
            newSubmission.text = this.answer;
            if (this.submission.id) {
                this.examParticipationService.createSubmission(newSubmission, this.textExercise.id);
            }
        }
    }

    /**
     * True, if the deadline is after the current date, or there is no deadline, or the exercise is always active
     */
    get isActive(): boolean {
        const isActive = this.textExercise && this.textExercise.dueDate && moment(this.textExercise.dueDate).isSameOrAfter(moment());
        return !!isActive;
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

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, undefined);
    }
}
