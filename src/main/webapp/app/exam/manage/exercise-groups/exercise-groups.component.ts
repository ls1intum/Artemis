import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TextExerciseImportComponent } from 'app/exercises/text/manage/text-exercise-import.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExerciseImportComponent } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { ModelingExerciseImportComponent } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faAngleDown, faAngleUp, faCheckDouble, faFileUpload, faFont, faKeyboard, faPlus, faProjectDiagram, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseImportComponent } from 'app/exercises/quiz/manage/quiz-exercise-import.component';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';

@Component({
    selector: 'jhi-exercise-groups',
    templateUrl: './exercise-groups.component.html',
    styleUrls: ['./exercise-groups.component.scss'],
})
export class ExerciseGroupsComponent implements OnInit {
    participationType = ProgrammingExerciseParticipationType;
    courseId: number;
    course: Course;
    examId: number;
    exam: Exam;
    exerciseGroups?: ExerciseGroup[];
    dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();
    exerciseType = ExerciseType;
    latestIndividualEndDate?: dayjs.Dayjs;
    exerciseGroupToExerciseTypesDict = new Map<number, ExerciseType[]>();

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFont = faFont;
    faWrench = faWrench;
    faCheckDouble = faCheckDouble;
    faFileUpload = faFileUpload;
    faKeyboard = faKeyboard;
    faProjectDiagram = faProjectDiagram;
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    constructor(
        private route: ActivatedRoute,
        private exerciseGroupService: ExerciseGroupService,
        public exerciseService: ExerciseService,
        private examManagementService: ExamManagementService,
        private eventManager: EventManager,
        private alertService: AlertService,
        private modalService: NgbModal,
        private router: Router,
    ) {}

    /**
     * Initialize the courseId and examId. Get all exercise groups for the exam. Setup dictionary for exercise groups which contain programming exercises.
     * See {@link setupExerciseGroupToExerciseTypesDict}.
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        // Only take action when a response was received for both requests
        forkJoin([this.loadExerciseGroups(), this.loadLatestIndividualEndDateOfExam()]).subscribe({
            next: ([examRes, examInfoDTO]) => {
                this.exam = examRes.body!;
                this.exerciseGroups = this.exam.exerciseGroups;
                this.course = this.exam.course!;
                this.latestIndividualEndDate = examInfoDTO ? examInfoDTO.body!.latestIndividualEndDate : undefined;
                this.setupExerciseGroupToExerciseTypesDict();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
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
     * it calls {@link setupExerciseGroupToExerciseTypesDict} to update the dictionary
     * @param exerciseId
     * @param exerciseGroupId
     */
    removeExercise(exerciseId: number, exerciseGroupId: number) {
        if (this.exerciseGroups) {
            this.exerciseGroups.forEach((exerciseGroup) => {
                if (exerciseGroup.id === exerciseGroupId && exerciseGroup.exercises && exerciseGroup.exercises.length > 0) {
                    exerciseGroup.exercises = exerciseGroup.exercises.filter((exercise) => exercise.id !== exerciseId);
                    this.setupExerciseGroupToExerciseTypesDict();
                }
            });
        }
    }

    /**
     * Delete the exercise group with the given id.
     * @param exerciseGroupId {number}
     * @param event representation of users choices to delete the student repositories and base repositories
     */
    deleteExerciseGroup(exerciseGroupId: number, event: { [key: string]: boolean }) {
        this.exerciseGroupService.delete(this.courseId, this.examId, exerciseGroupId, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'exerciseGroupOverviewModification',
                    content: 'Deleted an exercise group',
                });
                this.dialogErrorSource.next('');
                this.exerciseGroups = this.exerciseGroups!.filter((exerciseGroup) => exerciseGroup.id !== exerciseGroupId);
                delete this.exerciseGroupToExerciseTypesDict[exerciseGroupId];
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Get an icon for the type of the given exercise.
     * @param exercise {Exercise}
     */
    exerciseIcon(exercise: Exercise): IconProp {
        switch (exercise.type) {
            case ExerciseType.QUIZ:
                return faCheckDouble;
            case ExerciseType.FILE_UPLOAD:
                return faFileUpload;
            case ExerciseType.MODELING:
                return faProjectDiagram;
            case ExerciseType.PROGRAMMING:
                return faKeyboard;
            default:
                return faFont;
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
            case ExerciseType.QUIZ:
                const quizImportModalRef = this.modalService.open(QuizExerciseImportComponent, {
                    size: 'lg',
                    backdrop: 'static',
                });
                quizImportModalRef.result.then(
                    (result: QuizExercise) => {
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
        this.examManagementService.updateOrder(this.courseId, this.examId, this.exerciseGroups!).subscribe({
            next: (res) => (this.exerciseGroups = res.body!),
            error: () => this.alertService.error('artemisApp.examManagement.exerciseGroup.orderCouldNotBeSaved'),
        });
    }

    /**
     * sets up {@link exerciseGroupToExerciseTypesDict} that maps the exercise group id to whether the said exercise group contains a specific exercise type.
     * Used to show the correct modal for deleting exercises and to show only relevant information in the exercise tables.
     * E.g. in case programming exercises are present, the user must decide whether (s)he wants to delete the build plans.
     */
    setupExerciseGroupToExerciseTypesDict() {
        this.exerciseGroupToExerciseTypesDict = new Map<number, ExerciseType[]>();
        if (!this.exerciseGroups) {
            return;
        } else {
            for (const exerciseGroup of this.exerciseGroups) {
                this.exerciseGroupToExerciseTypesDict.set(exerciseGroup.id!, []);
                if (exerciseGroup.exercises) {
                    for (const exercise of exerciseGroup.exercises) {
                        this.exerciseGroupToExerciseTypesDict.get(exerciseGroup.id!)!.push(exercise.type!);
                    }
                }
            }
        }
    }

    /**
     * Opens the import module for an exam import
     */
    openExerciseGroupImportModal() {
        const examImportModalRef = this.modalService.open(ExamImportComponent, {
            size: 'xl',
            backdrop: 'static',
        });
        // The Exercise Group selection is performed within the exam-update.component afterwards
        examImportModalRef.componentInstance.subsequentExerciseGroupSelection = true;
        examImportModalRef.componentInstance.targetCourseId = this.courseId;
        examImportModalRef.componentInstance.targetExamId = this.examId;

        examImportModalRef.result.then(
            (exerciseGroups: ExerciseGroup[]) => {
                if (exerciseGroups) {
                    this.exerciseGroups = exerciseGroups;
                    this.alertService.success('artemisApp.examManagement.exerciseGroup.importSuccessful');
                }
            },
            () => {},
        );
    }
}
