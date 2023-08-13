import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ConsistencyCheckService } from 'app/shared/consistency-check/consistency-check.service';
import { AlertService } from 'app/core/util/alert.service';
import { ConsistencyCheckError } from 'app/entities/consistency-check-result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { getCourseId } from 'app/entities/exercise.model';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-consistency-check',
    templateUrl: './consistency-check.component.html',
})
export class ConsistencyCheckComponent implements OnInit {
    @Input() exercisesToCheck: ProgrammingExercise[];

    inconsistencies: ConsistencyCheckError[] = [];
    isLoading = true;

    // Icons
    faTimes = faTimes;
    faCheck = faCheck;

    constructor(private activeModal: NgbActiveModal, private consistencyCheckService: ConsistencyCheckService, private alertService: AlertService) {}

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
