import {
    Component,
    Input,
    Output,
    EventEmitter,
    OnInit,
    OnChanges,
    SimpleChanges,
    AfterViewInit,
    ViewChild,
    ViewChildren,
    QueryList
} from '@angular/core';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { AnswerOption } from '../../../entities/answer-option';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { AceEditorComponent } from 'ng2-ace-editor';

@Component({
    selector: 'jhi-re-evaluate-short-answer-question',
    templateUrl: './re-evaluate-short-answer-question.component.html',
    providers: [ArtemisMarkdown]
})
export class ReEvaluateShortAnswerQuestionComponent implements OnInit, AfterViewInit, OnChanges {
    @ViewChild('questionEditor')
    private questionEditor: AceEditorComponent;

    @ViewChildren(AceEditorComponent)
    aceEditorComponents: QueryList<AceEditorComponent>;

    @Input()
    question: ShortAnswerQuestion;
    @Input()
    questionIndex: number;

    @Output()
    questionDeleted = new EventEmitter<object>();
    @Output()
    questionUpdated = new EventEmitter<object>();
    @Output()
    questionMoveUp = new EventEmitter<object>();
    @Output()
    questionMoveDown = new EventEmitter<object>();

    /** Ace Editor configuration constants **/
    questionEditorText = '';
    editorMode = 'markdown';
    editorAutoUpdate = true;

    // Create Backup Question for resets
    backupQuestion: ShortAnswerQuestion;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit(): void {}

    ngAfterViewInit(): void {
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.question && changes.question.currentValue != null) {
            this.backupQuestion = Object.assign({}, this.question);
        }
    }
}
