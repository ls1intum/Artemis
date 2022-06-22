import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { HintType } from 'app/entities/hestia/exercise-hint.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/shared/code-hint.service';
import { faEye, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-code-hint-generation-step',
    templateUrl: './code-hint-generation-step.component.html',
    styleUrls: ['../../code-hint-generation-overview.component.scss'],
})
export class CodeHintGenerationStepComponent implements OnInit {
    @Input()
    exercise: ProgrammingExercise;

    @Output()
    onCodeHintsLoaded = new EventEmitter<CodeHint[]>();

    isLoading = false;
    codeHints?: CodeHint[];

    faWrench = faWrench;
    faEye = faEye;

    constructor(private exerciseService: ProgrammingExerciseService, private codeHintService: CodeHintService, private alertService: AlertService) {}

    ngOnInit() {
        this.isLoading = true;
        this.exerciseService.getCodeHintsForExercise(this.exercise.id!).subscribe({
            next: (res) => {
                this.codeHints = res.filter((entry) => entry.type === HintType.CODE);
                this.onCodeHintsLoaded.emit(this.codeHints);
                this.isLoading = false;
            },
            error: () => {},
        });
    }

    onGenerateCodeHints() {
        this.isLoading = true;
        this.codeHintService.generateCodeHintsForExercise(this.exercise.id!, true).subscribe({
            next: (res) => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.programmingExercise.generateCodeHintsSuccess',
                });
                this.codeHints = res.body!;
                this.onCodeHintsLoaded.emit(this.codeHints);
                this.isLoading = false;
            },
            error: () => {},
        });
    }
}
