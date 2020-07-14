import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/alert/alert.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TextExerciseImportComponent } from 'app/exercises/text/manage/text-exercise-import.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExerciseImportComponent } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { ModelingExerciseImportComponent } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-exercise-groups',
    templateUrl: './exercise-groups.component.html',
})
export class ExerciseGroupsComponent implements OnInit {
    courseId: number;
    course: Course;
    examId: number;
    exerciseGroups: ExerciseGroup[] | null;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    exerciseType = ExerciseType;

    constructor(
        private route: ActivatedRoute,
        private exerciseGroupService: ExerciseGroupService,
        private examManagementService: ExamManagementService,
        private courseManagementService: CourseManagementService,
        private jhiEventManager: JhiEventManager,
        private alertService: AlertService,
        private modalService: NgbModal,
        private router: Router,
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
        this.examManagementService.find(this.courseId, this.examId, false, true).subscribe(
            (res) => {
                this.exerciseGroups = res.body!.exerciseGroups;
                this.course = res.body!.course;
                this.courseManagementService.checkAndSetCourseRights(this.course);
            },
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
     * Opens the import module for a specific exercise type
     * @param exerciseGroup The current exercise group
     * @param exerciseType The exercise type you want to import
     */
    openImportModal(exerciseGroup: ExerciseGroup, exerciseType: ExerciseType) {
        const importBaseRoute = ['/course-management', this.courseId, 'exams', this.examId, 'exercise-groups', exerciseGroup.id, `${exerciseType}-exercises`, 'import'];

        switch (exerciseType) {
            case ExerciseType.PROGRAMMING:
                const programmingImportModalRef = this.modalService.open(ProgrammingExerciseImportComponent, {
                    size: 'lg',
                    backdrop: 'static',
                });
                programmingImportModalRef.result.then(
                    (result: ProgrammingExercise) => {
                        importBaseRoute.push(result.id);
                        this.router.navigate(importBaseRoute);
                    },
                    () => {},
                );
                break;
            case ExerciseType.TEXT:
                const textImportModalRef = this.modalService.open(TextExerciseImportComponent, {
                    size: 'lg',
                    backdrop: 'static',
                });
                textImportModalRef.result.then(
                    (result: TextExercise) => {
                        importBaseRoute.push(result.id);
                        this.router.navigate(importBaseRoute);
                    },
                    () => {},
                );
                break;
            case ExerciseType.MODELING:
                const modelingImportModalRef = this.modalService.open(ModelingExerciseImportComponent, {
                    size: 'lg',
                    backdrop: 'static',
                });
                modelingImportModalRef.result.then(
                    (result: ModelingExercise) => {
                        importBaseRoute.push(result.id);
                        this.router.navigate(importBaseRoute);
                    },
                    () => {},
                );
                break;
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
            () => this.alertService.error('artemisApp.examManagement.exerciseGroup.orderCouldNotBeSaved'),
        );
    }
}
