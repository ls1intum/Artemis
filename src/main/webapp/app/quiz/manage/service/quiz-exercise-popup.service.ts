import { Injectable, Type, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';

@Injectable({ providedIn: 'root' })
export class QuizExercisePopupService {
    private dialogService = inject(DialogService);
    private router = inject(Router);

    private dialogRef: DynamicDialogRef | undefined;

    /**
     * Open the dialog with the given content for the given exercise.
     * @param component the content that should be shown
     * @param quizExercise the quiz exercise for which the dialog should be shown
     * @param files required for the form data upload
     */
    open(component: Type<unknown>, quizExercise: QuizExercise, files: Map<string, File>): Promise<DynamicDialogRef | undefined> {
        return new Promise<DynamicDialogRef | undefined>((resolve) => {
            if (!this.dialogRef) {
                this.dialogRef = this.quizExerciseDialogRef(component, quizExercise, files);
            }
            resolve(this.dialogRef);
        });
    }

    /**
     * Open the dialog with the given content for the given exercise.
     * @param component the content that should be shown
     * @param quizExercise the quiz exercise for which the dialog should be shown
     * @param files required for the form data upload
     */
    quizExerciseDialogRef(component: Type<unknown>, quizExercise: QuizExercise, files: Map<string, File>): DynamicDialogRef | undefined {
        const ref = this.dialogService.open(component, {
            width: '50rem',
            breakpoints: {
                '850px': '95vw',
            },
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            resizable: false,
            showHeader: false,
            data: { quizExercise, files },
        });
        ref?.onClose.subscribe((result) => {
            this.dialogRef = undefined;
            if (result === 're-evaluate') {
                this.router.navigate(['/course-management/' + quizExercise.course!.id + '/quiz-exercises']);
            } else {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
            }
        });
        return ref ?? undefined;
    }
}
