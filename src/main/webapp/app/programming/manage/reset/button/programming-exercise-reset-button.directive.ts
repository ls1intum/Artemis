import { Directive, ElementRef, HostListener, OnDestroy, OnInit, Renderer2, inject, input } from '@angular/core';
import { ProgrammingExerciseResetDialogComponent } from 'app/programming/manage/reset/dialog/programming-exercise-reset-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TumAetUiDialogRef, TumAetUiDialogService, tumAetUiButtonClasses } from '@tumaet/ui-angular';

@Directive({ selector: '[jhiProgrammingExerciseResetButton]' })
export class ProgrammingExerciseResetButtonDirective implements OnInit, OnDestroy {
    private dialogService = inject(TumAetUiDialogService);
    private translateService = inject(TranslateService);
    private renderer = inject(Renderer2);
    private elementRef = inject(ElementRef);
    private dialogRef?: TumAetUiDialogRef;

    readonly programmingExercise = input.required<ProgrammingExercise>();

    ngOnInit() {
        for (const token of tumAetUiButtonClasses({ severity: 'danger', size: 'small', variant: 'solid' }).split(' ')) {
            this.renderer.addClass(this.elementRef.nativeElement, token);
        }
    }

    ngOnDestroy() {
        this.dialogRef?.dismiss();
    }

    /**
     * Function is executed when a MouseEvent is registered. Opens the reset Dialog
     * @param event
     */
    @HostListener('click', ['$event'])
    onClick(event: MouseEvent) {
        event.stopPropagation();
        this.dialogRef = this.dialogService.open(ProgrammingExerciseResetDialogComponent, {
            header: this.translateService.instant('entity.resetProgrammingExercise.title'),
            size: 'medium',
            closable: true,
            closeOnEscape: true,
            data: {
                programmingExercise: this.programmingExercise(),
            },
        });
    }
}
