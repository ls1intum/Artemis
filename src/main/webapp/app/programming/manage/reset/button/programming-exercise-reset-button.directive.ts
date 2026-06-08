import { Directive, ElementRef, HostListener, OnDestroy, OnInit, Renderer2, inject, input } from '@angular/core';
import { ProgrammingExerciseResetDialogComponent } from 'app/programming/manage/reset/dialog/programming-exercise-reset-dialog.component';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

@Directive({ selector: '[jhiProgrammingExerciseResetButton]' })
export class ProgrammingExerciseResetButtonDirective implements OnInit, OnDestroy {
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private renderer = inject(Renderer2);
    private elementRef = inject(ElementRef);
    private dialogRef?: DynamicDialogRef;

    readonly programmingExercise = input.required<ProgrammingExercise>();

    ngOnInit() {
        this.renderer.addClass(this.elementRef.nativeElement, 'btn');
        this.renderer.addClass(this.elementRef.nativeElement, 'btn-danger');
        this.renderer.addClass(this.elementRef.nativeElement, 'btn-sm');
    }

    ngOnDestroy() {
        this.dialogRef?.close();
    }

    /**
     * Function is executed when a MouseEvent is registered. Opens the reset Dialog
     * @param event
     */
    @HostListener('click', ['$event'])
    onClick(event: MouseEvent) {
        event.stopPropagation();
        this.dialogRef =
            this.dialogService.open(ProgrammingExerciseResetDialogComponent, {
                header: this.translateService.instant('entity.resetProgrammingExercise.title'),
                modal: true,
                closable: true,
                closeOnEscape: true,
                width: '50vw',
                data: {
                    programmingExercise: this.programmingExercise(),
                },
            }) ?? undefined;
    }
}
