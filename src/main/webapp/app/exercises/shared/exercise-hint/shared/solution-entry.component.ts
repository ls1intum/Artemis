import { Component, OnInit, OnDestroy, ViewChild, Input, Output, EventEmitter } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, Subscription } from 'rxjs';

import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ThemeService } from 'app/core/theme/theme.service';

@Component({
    selector: 'jhi-solution-entry',
    templateUrl: './solution-entry.component.html',
})
export class SolutionEntryComponent implements OnInit, OnDestroy {
    @ViewChild('editor', { static: true })
    editor: AceEditorComponent;

    @Input()
    solutionEntry: ProgrammingExerciseSolutionEntry;
    @Input()
    enableEditing: boolean;
    @Input()
    dialogError: Observable<string>;

    @Output()
    onRemoveEntry: EventEmitter<any> = new EventEmitter();

    themeSubscription: Subscription;
    faTimes = faTimes;

    constructor(protected route: ActivatedRoute, private themeService: ThemeService) {}

    ngOnInit() {
        this.setupEditor();
    }

    ngOnDestroy(): void {
        this.themeSubscription.unsubscribe();
    }

    emitRemovalEvent() {
        this.onRemoveEntry.emit();
    }

    private setupEditor() {
        const line = this.solutionEntry.line;

        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.editor.setTheme(theme.codeAceTheme);
        });
        this.editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: Infinity,
            showPrintMargin: false,
        });
        // Ensure that the line counter is according to the solution entry
        this.editor.getEditor().session.gutterRenderer = {
            getWidth(session: any, lastLineNumber: number, config: any) {
                return this.getText(session, lastLineNumber).toString().length * config.characterWidth;
            },
            getText(session: any, row: number): string | number {
                return !line ? '' : row + line;
            },
        };
        this.editor.getEditor().getSession().setValue(this.solutionEntry.code);
        this.editor.getEditor().resize();
    }
}
