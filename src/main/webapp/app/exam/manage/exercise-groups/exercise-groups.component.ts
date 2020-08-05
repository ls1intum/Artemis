import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of, forkJoin } from 'rxjs';
import { catchError } from 'rxjs/operators';
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
import { Exam } from 'app/entities/exam.model';
import { Moment } from 'moment';

@Component({
    selector: 'jhi-exercise-groups',
    templateUrl: './exercise-groups.component.html',
})
export class ExerciseGroupsComponent implements OnInit {
    courseId: number;
    course: Course;
    examId: number;
    exam: Exam;
    exerciseGroups: ExerciseGroup[] | null;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    exerciseType = ExerciseType;
    latestIndividualEndDate: Moment | null;
    exerciseGroupContainsProgrammingExerciseDict: { [id: number]: boolean } = {};

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
     * Initialize the courseId and examId. Get all exercise groups for the exam. Setup dictionary for exercise groups which contain programming exercises.
     * See {@link setupExerciseGroupContainsProgrammingExerciseDict}.
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        // Only take action when a response was received for both requests
        forkJoin(this.loadExerciseGroups(), this.loadLatestIndividualEndDateOfExam()).subscribe(
            ([examRes, examInfoDTO]) => {
                this.exam = examRes.body!;
                this.exerciseGroups = examRes.body!.exerciseGroups;
                this.course = examRes.body!.course;
                this.courseManagementService.checkAndSetCourseRights(this.course);
                this.latestIndividualEndDate = examInfoDTO ? examInfoDTO.body!.latestIndividualEndDate : null;
                this.setupExerciseGroupContainsProgrammingExerciseDict();
            },
            (res: HttpErrorResponse) => onError(this.alertService, res),
        );
    }

    /**
     * Load the latest individual end date of the exam. If this the HTTP response is erroneous, an observables emitting
     * null will be returned
     */
    loadLatestIndividualEndDateOfExam() {
        return this.examManagementService.getLatestIndividualEndDateOfExam(this.courseId, this.examId).pipe(
            // When the exam start date was not set properly an error will be thrown.
            // Catch this in the inner observable otherwise forkJoin won't return data
            catchError(() => {
                return of(null);
            }),
        );
    }

    /**
     * Load all exercise groups of the current exam.
     */
    loadExerciseGroups() {
        return this.examManagementService.find(this.courseId, this.examId, false, true);
    }

    /**
     * Remove the exercise with the given exerciseId from the exercise group with the given exerciseGroupId. In case the removed exercise was a Programming Exercise,
     * it calls {@link setupExerciseGroupContainsProgrammingExerciseDict} to update the dictionary
     * @param exerciseId
     * @param exerciseGroupId
     * @param programmingExercise flag that indicates if the deleted exercise was a programming exercise. Default value is false.
     */
    removeExercise(exerciseId: number, exerciseGroupId: number, programmingExercise = false) {
        if (this.exerciseGroups) {
            this.exerciseGroups.forEach((exerciseGroup) => {
                if (exerciseGroup.id === exerciseGroupId && exerciseGroup.exercises && exerciseGroup.exercises.length > 0) {
                    exerciseGroup.exercises = exerciseGroup.exercises.filter((exercise) => exercise.id !== exerciseId);
                    if (programmingExercise) {
                        this.setupExerciseGroupContainsProgrammingExerciseDict();
                    }
                }
            });
        }
    }

    /**
     * Delete the exercise group with the given id.
     * @param exerciseGroupId {number}
     * @param $event representation of users choices to delete the student repositories and base repositories
     */
    deleteExerciseGroup(exerciseGroupId: number, $event: { [key: string]: boolean }) {
        this.exerciseGroupService.delete(this.courseId, this.examId, exerciseGroupId, $event.deleteStudentReposBuildPlans, $event.deleteBaseReposBuildPlans).subscribe(
            () => {
                this.jhiEventManager.broadcast({
                    name: 'exerciseGroupOverviewModification',
                    content: 'Deleted an exercise group',
                });
                this.dialogErrorSource.next('');
                this.exerciseGroups = this.exerciseGroups!.filter((exerciseGroup) => exerciseGroup.id !== exerciseGroupId);
                delete this.exerciseGroupContainsProgrammingExerciseDict[exerciseGroupId];
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

    /**
     * sets up {@link exerciseGroupContainsProgrammingExerciseDict} that maps the exercise group id to whether the said exercise group contains programming exercises.
     * Used to show the correct modal for deleting exercises.
     * In case programming exercises are present, the user must decide whether (s)he wants to delete the build plans.
     */
    setupExerciseGroupContainsProgrammingExerciseDict() {
        this.exerciseGroupContainsProgrammingExerciseDict = {};
        if (this.exerciseGroups == null) {
            return;
        } else {
            for (const exerciseGroup of this.exerciseGroups) {
                if (exerciseGroup.exercises != null) {
                    for (const exercise of exerciseGroup.exercises) {
                        if (exercise.type === ExerciseType.PROGRAMMING) {
                            this.exerciseGroupContainsProgrammingExerciseDict[exerciseGroup.id] = true;
                            break;
                        }
                    }
                    if (!(exerciseGroup.id in this.exerciseGroupContainsProgrammingExerciseDict)) {
                        this.exerciseGroupContainsProgrammingExerciseDict[exerciseGroup.id] = false;
                    }
                }
            }
        }
    }
}
