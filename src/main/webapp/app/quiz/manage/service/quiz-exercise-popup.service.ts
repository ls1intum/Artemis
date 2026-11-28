import { Service, Type, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogRef, TumAetUiDialogService } from '@tumaet/ui-angular/dialog';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';

@Service()
export class QuizExercisePopupService {
    private dialogService = inject(TumAetUiDialogService);
    private router = inject(Router);
    private translateService = inject(TranslateService);

    private dialogRef: TumAetUiDialogRef | undefined;

    /**
     * Open the dialog with the given content for the given exercise.
     * @param component the content that should be shown
     * @param quizExercise the quiz exercise for which the dialog should be shown
     * @param files required for the form data upload
     */
    open(component: Type<unknown>, quizExercise: QuizExercise, files: Map<string, File>): Promise<TumAetUiDialogRef | undefined> {
        return new Promise<TumAetUiDialogRef | undefined>((resolve) => {
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
    quizExerciseDialogRef(component: Type<unknown>, quizExercise: QuizExercise, files: Map<string, File>): TumAetUiDialogRef | undefined {
        const ref = this.dialogService.open(component, {
            size: 'medium',
            closeOnEscape: true,
            dismissableMask: false,
            showHeader: false,
            ariaLabel: this.translateService.instant('artemisApp.quizExercise.re-evaluate.warning.warning'),
            data: { quizExercise, files },
        });
        ref?.onClose.subscribe((result) => {
            this.dialogRef = undefined;
            if (result === 're-evaluate') {
                void this.router.navigate(['/course-management/' + quizExercise.course!.id + '/quiz-exercises']);
            } else {
                void this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
            }
        });
        return ref ?? undefined;
    }
}
