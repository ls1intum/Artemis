import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { faWrench } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/services/code-hint.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-code-hint-generation-step',
    templateUrl: './code-hint-generation-step.component.html',
    styleUrls: ['../../code-hint-generation-overview/code-hint-generation-overview.component.scss'],
})
export class CodeHintGenerationStepComponent implements OnInit {
    @Input()
    exercise: ProgrammingExercise;

    @Output()
    onCodeHintsLoaded = new EventEmitter<CodeHint[]>();

    isLoading = false;
    codeHints?: CodeHint[];

    faWrench = faWrench;

    constructor(private exerciseService: ProgrammingExerciseService, private codeHintService: CodeHintService, private alertService: AlertService) {}

    ngOnInit() {
        this.isLoading = true;
        this.exerciseService.getCodeHintsForExercise(this.exercise.id!).subscribe({
            next: (codeHints) => {
                this.codeHints = codeHints;
                this.onCodeHintsLoaded.emit(this.codeHints);
                this.isLoading = false;
            },
            error: (error) => {
                this.isLoading = false;
                this.alertService.error(error.message);
            },
        });
    }

    generateCodeHints(deleteOldHints: boolean) {
        this.isLoading = true;
        this.codeHintService.generateCodeHintsForExercise(this.exercise?.id!, deleteOldHints).subscribe({
            next: (generatedHints) => {
                if (deleteOldHints) {
                    this.codeHints = generatedHints;
                } else {
                    generatedHints.forEach((generatedHint) => this.codeHints?.push(generatedHint));
                }
                this.onCodeHintsLoaded.emit(this.codeHints);
                this.isLoading = false;
                this.alertService.success('artemisApp.codeHint.management.step4.' + (deleteOldHints ? 'recreateHintsButton' : 'updateHintsButton') + '.success');
            },
            error: (error) => {
                this.isLoading = false;
                this.alertService.error(error.message);
            },
        });
    }
}
