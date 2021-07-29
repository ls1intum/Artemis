import { Component, OnInit, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ConsistencyCheckService } from 'app/shared/consistency-check/consistency-check.service';
import { JhiAlertService } from 'ng-jhipster';
import { ConsistencyCheckError } from 'app/entities/consistency-check-result.model';
import { getCourseId } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-consistency-check',
    templateUrl: './consistency-check.component.html',
    styles: [],
})
export class ConsistencyCheckComponent implements OnInit {
    @Input() id: number;
    @Input() checkType: CheckType;

    errors: ConsistencyCheckError[];
    courseId: number | undefined;

    constructor(private activeModal: NgbActiveModal, private consistencyCheckService: ConsistencyCheckService, private jhiAlertService: JhiAlertService) {}

    ngOnInit(): void {
        if (this.checkType === CheckType.PROGRAMMING_EXERCISE) {
            this.consistencyCheckService.checkConsistencyForProgrammingExercise(this.id).subscribe(
                (errors) => {
                    this.errors = errors;
                    if (this.errors.length > 0) {
                        this.courseId = getCourseId(this.errors[0].programmingExercise!);
                    }
                },
                (err) => {
                    this.jhiAlertService.error(err);
                },
            );
        } else if (this.checkType === CheckType.COURSE) {
            this.consistencyCheckService.checkConsistencyForCourse(this.id).subscribe(
                (errors) => {
                    this.errors = errors;
                },
                (err) => {
                    this.jhiAlertService.error(err);
                },
            );
            this.courseId = this.id;
        } else {
            this.jhiAlertService.error('No check type specified');
        }
    }

    isLoadingResults() {
        return this.errors === undefined;
    }

    closeModal() {
        this.activeModal.close();
    }
}

export enum CheckType {
    PROGRAMMING_EXERCISE,
    COURSE,
}
