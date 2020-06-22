import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/alert/alert.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';

@Component({
    selector: 'jhi-exercise-groups',
    templateUrl: './exercise-groups.component.html',
})
export class ExerciseGroupsComponent implements OnInit {
    courseId: number;
    examId: number;
    exerciseGroups: ExerciseGroup[] | null;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    isSavingOrder = false;

    constructor(
        private route: ActivatedRoute,
        private exerciseGroupService: ExerciseGroupService,
        private examManagementService: ExamManagementService,
        private jhiEventManager: JhiEventManager,
        private alertService: AlertService,
    ) {}

    /**
     * Initialize the courseId and examId. Get all exercise groups for the exam.
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.loadExerciseGroups();
    }

    /**
     * Load all exercise groups of the current exam.
     */
    loadExerciseGroups() {
        this.exerciseGroupService.findAllForExam(this.courseId, this.examId).subscribe(
            (res) => (this.exerciseGroups = res.body),
            (res: HttpErrorResponse) => onError(this.alertService, res),
        );
    }

    /**
     * Delete the exercise group with the given id.
     * @param exerciseGroupId {number}
     */
    deleteExerciseGroup(exerciseGroupId: number) {
        this.exerciseGroupService.delete(this.courseId, this.examId, exerciseGroupId).subscribe(
            () => {
                this.jhiEventManager.broadcast({
                    name: 'exerciseGroupOverviewModification',
                    content: 'Deleted an exercise group',
                });
                this.dialogErrorSource.next('');
                this.loadExerciseGroups();
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Get an icon for the type of the given exercise.
     * @param exercise {Exercise}
     */
    exerciseIcon(exercise: Exercise): string {
        switch (exercise.type) {
            case ExerciseType.QUIZ:
                return 'check-double';
            case ExerciseType.FILE_UPLOAD:
                return 'file-upload';
            case ExerciseType.MODELING:
                return 'project-diagram';
            case ExerciseType.PROGRAMMING:
                return 'keyboard';
            default:
                return 'font';
        }
    }

    /**
     * Move the exercise group up one position in the order
     * @param index of the exercise group in the exerciseGroups array
     */
    moveUp(index: number): void {
        if (this.exerciseGroups) {
            [this.exerciseGroups[index], this.exerciseGroups[index - 1]] = [this.exerciseGroups[index - 1], this.exerciseGroups[index]];
        }
        this.saveOrder();
    }

    /**
     * Move the exercise group down one position in the order
     * @param index of the exercise group in the exerciseGroups array
     */
    moveDown(index: number): void {
        if (this.exerciseGroups) {
            [this.exerciseGroups[index], this.exerciseGroups[index + 1]] = [this.exerciseGroups[index + 1], this.exerciseGroups[index]];
        }
        this.saveOrder();
    }

    private saveOrder(): void {
        this.examManagementService.updateOrder(this.courseId, this.examId, this.exerciseGroups!).subscribe(
            (res) => (this.exerciseGroups = res.body),
            (err) => {
                this.alertService.error('artemisApp.examManagement.exerciseGroup.orderCouldNotBeSaved');
                console.log(err);
            },
        );
    }
}
