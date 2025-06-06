import { Component, Input, OnInit, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';

@Component({
    selector: 'jhi-consistency-check',
    templateUrl: './consistency-check.component.html',
    imports: [TranslateDirective, FaIconComponent, RouterLink],
})
export class ConsistencyCheckComponent implements OnInit {
    private activeModal = inject(NgbActiveModal);
    private consistencyCheckService = inject(ConsistencyCheckService);
    private alertService = inject(AlertService);

    @Input() exercisesToCheck: ProgrammingExercise[];

    inconsistencies: ConsistencyCheckError[] = [];
    isLoading = true;

    // Icons
    faTimes = faTimes;
    faCheck = faCheck;

    ngOnInit(): void {
        this.isLoading = true;
        let exercisesRemaining = this.exercisesToCheck.length;
        this.exercisesToCheck.forEach((exercise) => {
            const course = getCourseId(exercise);
            this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id!).subscribe({
                next: (inconsistencies) => {
                    this.inconsistencies = this.inconsistencies.concat(inconsistencies);
                    this.inconsistencies.map((inconsistency) => (inconsistency.programmingExerciseCourseId = course || undefined));
                    if (--exercisesRemaining === 0) {
                        this.isLoading = false;
                    }
                },
                error: (err) => {
                    this.alertService.error(err);
                    this.isLoading = false;
                },
            });
        });
    }

    closeModal() {
        this.activeModal.close();
    }
}
