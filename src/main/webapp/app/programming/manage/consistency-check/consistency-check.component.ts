import { Component, OnInit, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';

interface ConsistencyCheckData {
    exercisesToCheck: ProgrammingExercise[];
}

@Component({
    selector: 'jhi-consistency-check',
    templateUrl: './consistency-check.component.html',
    imports: [TranslateDirective, FaIconComponent, RouterLink],
})
export class ConsistencyCheckComponent implements OnInit {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);
    private consistencyCheckService = inject(ConsistencyCheckService);
    private alertService = inject(AlertService);

    private readonly data = this.dialogConfig.data as ConsistencyCheckData | undefined;

    readonly exercisesToCheck = signal<ProgrammingExercise[]>(this.data?.exercisesToCheck ?? []);

    readonly inconsistencies = signal<ConsistencyCheckError[]>([]);
    readonly isLoading = signal(true);

    // Icons
    faTimes = faTimes;
    faCheck = faCheck;

    ngOnInit(): void {
        this.isLoading.set(true);
        const exercisesToCheck = this.exercisesToCheck();
        if (exercisesToCheck.length === 0) {
            this.isLoading.set(false);
            return;
        }
        let exercisesRemaining = exercisesToCheck.length;
        exercisesToCheck.forEach((exercise) => {
            const course = getCourseId(exercise);
            this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id!).subscribe({
                next: (inconsistencies) => {
                    const updatedInconsistencies = this.inconsistencies().concat(inconsistencies);
                    updatedInconsistencies.map((inconsistency) => (inconsistency.programmingExerciseCourseId = course || undefined));
                    this.inconsistencies.set(updatedInconsistencies);
                    if (--exercisesRemaining === 0) {
                        this.isLoading.set(false);
                    }
                },
                error: (err) => {
                    this.alertService.error(err);
                    this.isLoading.set(false);
                },
            });
        });
    }

    closeModal() {
        this.dialogRef.close();
    }
}
