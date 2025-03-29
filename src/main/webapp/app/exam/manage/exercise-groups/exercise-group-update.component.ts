import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { Observable } from 'rxjs';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { onError } from 'app/shared/util/global.utils';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { NgbAlert } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-exercise-group-update',
    templateUrl: './exercise-group-update.component.html',
    imports: [TranslateDirective, FormsModule, NgbAlert, FaIconComponent],
})
export class ExerciseGroupUpdateComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private exerciseGroupService = inject(ExerciseGroupService);
    private alertService = inject(AlertService);

    readonly alertType = 'info';
    courseId: number;
    exam: Exam;
    exerciseGroup: ExerciseGroup;
    isSaving = false;
    // Icons
    faBan = faBan;
    faSave = faSave;

    /**
     * Initialize the courseId and exerciseGroup
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.parent?.parent?.snapshot.paramMap.get('courseId'));
        this.route.data.subscribe(({ exam, exerciseGroup }) => {
            this.exam = exam;
            this.exerciseGroup = exerciseGroup;
        });
    }

    /**
     * Create the exercise group if no id is set.
     * Update the exercise group if an id is set.
     */
    save() {
        this.isSaving = true;
        if (this.exerciseGroup.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseGroupService.update(this.courseId, this.exam.id!, this.exerciseGroup));
        } else {
            this.exerciseGroup.exam = this.exam;
            this.subscribeToSaveResponse(this.exerciseGroupService.create(this.courseId, this.exam.id!, this.exerciseGroup));
        }
    }

    previousState() {
        this.router.navigate(['course-management', this.courseId, 'exams', this.route.snapshot.paramMap.get('examId'), 'exercise-groups']);
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ExerciseGroup>>) {
        result.subscribe({
            next: () => this.onSaveSuccess(),
            error: (err: HttpErrorResponse) => this.onSaveError(err),
        });
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        onError(this.alertService, error);
        this.isSaving = false;
    }
}
