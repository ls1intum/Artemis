import { AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit, inject, viewChild, viewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamPage } from 'app/exam/shared/entities/exam-page.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { ExamNavigationBarComponent } from 'app/exam/overview/exam-navigation-bar/exam-navigation-bar.component';
import { ModelingExamSubmissionComponent } from 'app/exam/overview/exercises/modeling/modeling-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/overview/exercises/quiz/quiz-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/overview/exercises/text/text-exam-submission.component';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import dayjs from 'dayjs/esm';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { Observable, Subscription, forkJoin, map, mergeMap, tap, toArray } from 'rxjs';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { FileUploadExamSubmissionComponent } from 'app/exam/overview/exercises/file-upload/file-upload-exam-submission.component';
import { SubmissionVersionService } from 'app/exercise/submission-version/submission-version.service';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { ExamPageComponent } from 'app/exam/overview/exercises/exam-page.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MatSlider, MatSliderThumb } from '@angular/material/slider';
import { FormsModule } from '@angular/forms';
import { toObservable } from '@angular/core/rxjs-interop';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';

@Component({
    selector: 'jhi-student-exam-timeline',
    templateUrl: './student-exam-timeline.component.html',
    styleUrls: ['./student-exam-timeline.component.scss'],
    imports: [
        TranslateDirective,
        MatSlider,
        MatSliderThumb,
        FormsModule,
        ExamNavigationBarComponent,
        QuizExamSubmissionComponent,
        FileUploadExamSubmissionComponent,
        TextExamSubmissionComponent,
        ModelingExamSubmissionComponent,
        ProgrammingExerciseExamDiffComponent,
    ],
})
export class StudentExamTimelineComponent implements OnInit, AfterViewInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private submissionService = inject(SubmissionService);
    private submissionVersionService = inject(SubmissionVersionService);
    private cdr = inject(ChangeDetectorRef);

    readonly ExerciseType = ExerciseType;
    readonly SubmissionVersion = SubmissionVersion;

    // stores if a page component has already been visited (true) or not (false)
    // this is an array because the exam-timeline uses a page component for each exercise
    pageComponentVisited: boolean[];
    selectedTimestamp = 0;
    timestampIndex = 0;

    studentExam: StudentExam;
    exerciseIndex: number;
    activeExamPage = new ExamPage();
    submissionTimeStamps: dayjs.Dayjs[] = [];
    submissionVersions: SubmissionVersion[] = [];
    programmingSubmissions: ProgrammingSubmission[] = [];
    fileUploadSubmissions: FileUploadSubmission[] = [];

    currentExercise: Exercise | undefined;
    currentSubmission: SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined;
    changesSubscription: Subscription;
    cachedDiffInformation: Map<string, RepositoryDiffInformation> = new Map<string, RepositoryDiffInformation>();

    currentPageComponents = viewChildren(ExamSubmissionComponent);
    examNavigationBarComponent = viewChild.required<ExamNavigationBarComponent>('examNavigationBar');

    private activatedRouteSubscription: Subscription;

    ngOnInit(): void {
        this.activatedRouteSubscription = this.activatedRoute.data.subscribe(({ studentExam: studentExamWithGrade }) => {
            this.studentExam = studentExamWithGrade.studentExam;
        });
        this.exerciseIndex = 0;
        this.pageComponentVisited = new Array(this.studentExam.exercises!.length).fill(false);
        this.retrieveSubmissionDataAndTimeStamps().subscribe((results) => {
            const allSubmissions = results.flat();
            allSubmissions.forEach((result) => {
                //workaround because instanceof does not work.
                if (this.isSubmissionVersion(result)) {
                    this.submissionVersions.push(result as SubmissionVersion);
                } else if (this.isFileUploadSubmission(result)) {
                    this.fileUploadSubmissions.push(result as FileUploadSubmission);
                } else {
                    this.programmingSubmissions.push(result as ProgrammingSubmission);
                }
                this.submissionTimeStamps.push(this.getSubmissionTimestamp(result));
            });
            this.sortTimeStamps();
            this.selectedTimestamp = this.submissionTimeStamps[0]?.toDate().getTime() ?? 0;
            const firstSubmission = this.findFirstSubmission();
            this.currentSubmission = firstSubmission;
            this.exerciseIndex = this.findExerciseIndex(firstSubmission!);
            this.examNavigationBarComponent().changePage(false, this.exerciseIndex, false, firstSubmission);
        });
    }

    ngAfterViewInit(): void {
        this.changesSubscription = this.currentPageComponentsChanges.subscribe(() => {
            this.updateSubmissionOrSubmissionVersionInView();
        });
    }
    ngOnDestroy(): void {
        this.activatedRouteSubscription?.unsubscribe();
        this.changesSubscription?.unsubscribe();
    }

    /**
     * Updates the view for a programming exercise by setting the correct submission for the component
     * This does not really work yet because the programming submission is not updated in the view
     * This will change in the followup PR #7097
     * Programming exercises do not support submission versions and therefore, need to be handled differently.
     */
    private updateProgrammingExerciseView() {
        const activeProgrammingComponent = this.activePageComponent as ProgrammingExerciseExamDiffComponent | undefined;
        if (activeProgrammingComponent) {
            activeProgrammingComponent.studentParticipation.update(() => this.currentExercise!.studentParticipations![0]);
            activeProgrammingComponent.exercise.update(() => this.currentExercise!);
            activeProgrammingComponent.currentSubmission.update(() => this.currentSubmission as ProgrammingSubmission);
            activeProgrammingComponent.previousSubmission.update(() => this.findPreviousProgrammingSubmission(this.currentExercise!, this.currentSubmission!));
            activeProgrammingComponent.submissions.update(() =>
                this.programmingSubmissions.filter((submission) => submission.participation?.exercise?.id === this.currentExercise?.id),
            );
            activeProgrammingComponent.exerciseIdSubject.update((subject) => {
                subject.next(this.currentExercise!.id!);
                return subject;
            });
        }
    }

    findPreviousProgrammingSubmission(currentExercise: Exercise, currentSubmission: ProgrammingSubmission): ProgrammingSubmission | undefined {
        return this.findClosestSubmission(
            this.programmingSubmissions,
            (s) => s.submissionDate!,
            (s) => s.participation?.exercise?.id === currentExercise.id,
            currentSubmission.submissionDate!,
            true,
        );
    }

    /**
     * Updates the view for a file upload exercise by setting the correct submission for the component
     * File Upload exercises do not support submission versions and therefore, need to be handled differently.
     */
    private updateFileUploadExerciseView() {
        const fileUploadComponent = this.activePageComponent as FileUploadExamSubmissionComponent;
        if (fileUploadComponent) {
            fileUploadComponent.studentSubmission.update(() => this.currentSubmission as FileUploadSubmission);
            fileUploadComponent.updateViewFromSubmission();
        }
    }

    displayCurrentTimestamp(): string {
        return dayjs(this.selectedTimestamp).format('HH:mm:ss');
    }

    /**
     * Checks if the submission is a submission version
     * Instanceof does not work here because it always returns false.
     * @param object the object to check
     */
    isSubmissionVersion(object: SubmissionVersion | Submission | undefined) {
        if (!object) {
            return false;
        }
        const submissionVersion = object as SubmissionVersion;
        // submissionVersion.content is intentionally not checked because it can be undefined if the content is empty
        return submissionVersion.id && submissionVersion.createdDate && submissionVersion.submission;
    }

    /**
     * Helper method to check if the object is a file upload submission
     * Instanceof does not work here because we have submission objects that are is only a super class of FileUploadSubmission
     * @param object the object to check
     */
    private isFileUploadSubmission(object: FileUploadSubmission | SubmissionVersion | ProgrammingSubmission | undefined) {
        if (!object) {
            return false;
        }
        const fileUploadSubmission = object as FileUploadSubmission;
        return !!fileUploadSubmission.id && fileUploadSubmission.submissionDate && fileUploadSubmission.filePath;
    }

    /**
     * Retrieve all submission versions/submissions for all exercises of the exam
     */
    retrieveSubmissionDataAndTimeStamps() {
        const submissionObservables: Observable<SubmissionVersion[] | Submission[]>[] = [];
        this.studentExam.exercises?.forEach((exercise) => {
            if (exercise.type === ExerciseType.PROGRAMMING || exercise.type === ExerciseType.FILE_UPLOAD) {
                const id = exercise.studentParticipations![0].id!;
                submissionObservables.push(this.submissionService.findAllSubmissionsOfParticipation(id).pipe(map(({ body }) => body!)));
            } else {
                submissionObservables.push(
                    this.submissionVersionService.findAllSubmissionVersionsOfSubmission(exercise.studentParticipations![0].submissions![0].id!).pipe(
                        mergeMap((versions) => versions),
                        toArray(),
                    ),
                );
            }
        });

        return forkJoin([...submissionObservables]).pipe(tap(() => this.cdr.detectChanges()));
    }

    /**
     * Sorts the time stamps in ascending order
     */
    private sortTimeStamps() {
        this.submissionTimeStamps = this.submissionTimeStamps.sort((date1, date2) => (date1.isAfter(date2) ? 1 : -1));
    }

    /**
     * This method is called when the user clicks on the next or previous button in the navigation bar or on the slider.
     * @param exerciseChange contains the exercise to which the user wants to navigate to and the submission that should be displayed
     */
    onPageChange(exerciseChange: { exercise?: Exercise; submission?: ProgrammingSubmission | SubmissionVersion | FileUploadSubmission }): void {
        this.activePageComponent?.onDeactivate();

        if (!exerciseChange.submission && exerciseChange.exercise !== this.currentExercise) {
            exerciseChange.submission = this.findSubmissionForExerciseClosestToCurrentTimeStampForExercise(exerciseChange.exercise!);
        }

        if (exerciseChange.submission) {
            this.selectedTimestamp = this.getSubmissionTimestamp(exerciseChange.submission).toDate().getTime();
            this.currentSubmission = exerciseChange.submission;
            this.initializeExercise(exerciseChange.exercise!, exerciseChange.submission);
        }
    }

    /**
     * Finds the first submission of the student exam
     * Used to determine what's the first submission and hence, the first exercise to display.
     */

    private findFirstSubmission(): FileUploadSubmission | SubmissionVersion | ProgrammingSubmission | undefined {
        return this.findCorrespondingSubmissionForTimestamp(this.submissionTimeStamps[0]?.valueOf() ?? 0);
    }

    initializeExercise(exercise: Exercise, submission: Submission | SubmissionVersion | undefined) {
        this.activeExamPage.exercise = exercise;
        // set current exercise index
        this.exerciseIndex = this.studentExam.exercises!.findIndex((exercise1) => exercise1.id === exercise.id);
        this.currentExercise = exercise;
        this.currentSubmission = submission;
        this.activateActiveComponent();
        this.updateSubmissionOrSubmissionVersionInView();
    }

    private updateSubmissionOrSubmissionVersionInView() {
        if (this.currentExercise?.type === ExerciseType.PROGRAMMING) {
            this.updateProgrammingExerciseView();
        } else if (this.currentExercise?.type === ExerciseType.FILE_UPLOAD) {
            this.updateFileUploadExerciseView();
        } else {
            const activePageComponent = this.activePageComponent as ExamSubmissionComponent | undefined;
            activePageComponent?.setSubmissionVersion(this.currentSubmission as SubmissionVersion);
        }
    }

    private activateActiveComponent() {
        this.pageComponentVisited[this.activePageIndex] = true;
        const activeComponent = this.activePageComponent;
        if (activeComponent) {
            activeComponent.onActivate();
        }
    }

    get activePageIndex(): number {
        return this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === this.activeExamPage.exercise?.id);
    }

    get activePageComponent(): ExamPageComponent | undefined {
        // we have to find the current component based on the activeExercise because the queryList might not be full yet (e.g. only 2 of 5 components initialized)
        return this.currentPageComponents().find((submissionComponent) => (submissionComponent as ExamSubmissionComponent).getExercise().id === this.activeExamPage.exercise?.id);
    }

    /**
     * This method is called when the user clicks on the slider
     */
    onSliderInputChange() {
        this.selectedTimestamp = this.submissionTimeStamps[this.timestampIndex].toDate().getTime();
        const submission = this.findCorrespondingSubmissionForTimestamp(this.selectedTimestamp);
        this.currentExercise = this.getSubmissionExercise(submission!);
        const exerciseIndex = this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === this.currentExercise?.id);
        this.exerciseIndex = exerciseIndex;
        this.currentSubmission = submission;
        this.examNavigationBarComponent().changePage(false, exerciseIndex, false, submission);
    }

    /**
     * Finds the submission for the current timestamp.
     * @param timestamp The timestamp for which the submission should be found.
     */
    private findCorrespondingSubmissionForTimestamp(timestamp: number): SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined {
        const comparisonObject = dayjs(timestamp);
        const allSubmissions: (SubmissionVersion | ProgrammingSubmission | FileUploadSubmission)[] = [
            ...this.submissionVersions,
            ...this.programmingSubmissions,
            ...this.fileUploadSubmissions,
        ];
        return allSubmissions.find((submission) => this.getSubmissionTimestamp(submission).isSame(comparisonObject));
    }

    /**
     * Finds the submission for the exercise with the closest timestamp to the current timestamp.
     * @param exercise The exercise for which the submission should be found.
     */
    private findSubmissionForExerciseClosestToCurrentTimeStampForExercise(exercise: Exercise): SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined {
        if (exercise.type === ExerciseType.FILE_UPLOAD) {
            return this.fileUploadSubmissions.find((submission) => submission.participation?.exercise?.id === exercise.id);
        }
        const comparisonObject = dayjs(this.selectedTimestamp);
        const exerciseFilter = (id: number | undefined) => id === exercise.id;
        if (exercise.type === ExerciseType.PROGRAMMING) {
            return this.findClosestSubmission(
                this.programmingSubmissions,
                (s) => s.submissionDate!,
                (s) => exerciseFilter(s.participation?.exercise?.id),
                comparisonObject,
            );
        }
        return this.findClosestSubmission(
            this.submissionVersions,
            (s) => s.createdDate,
            (s) => exerciseFilter(s.submission?.participation?.exercise?.id),
            comparisonObject,
        );
    }

    /**
     * Finds the closest submission in an array to a given comparison timestamp.
     * @param items the array of items to search
     * @param getTimestamp function to extract the timestamp from an item
     * @param filterFn function to filter items (e.g. by exercise)
     * @param comparisonTimestamp the reference timestamp to compare against
     * @param beforeOnly if true, only considers items with timestamps before the comparison timestamp
     */
    private findClosestSubmission<T>(
        items: T[],
        getTimestamp: (item: T) => dayjs.Dayjs,
        filterFn: (item: T) => boolean,
        comparisonTimestamp: dayjs.Dayjs,
        beforeOnly: boolean = false,
    ): T | undefined {
        let smallestDiff = Infinity;
        let closest: T | undefined;
        const matchCount = items.filter(filterFn).length;
        for (const item of items) {
            const timestamp = getTimestamp(item);
            const diff = Math.abs(timestamp.diff(comparisonTimestamp));
            const matchesExercise = filterFn(item);
            const notSameOrOnly = !timestamp.isSame(comparisonTimestamp) || matchCount === 1;
            if (matchesExercise && diff < smallestDiff && (!beforeOnly || timestamp.isBefore(comparisonTimestamp)) && (beforeOnly || notSameOrOnly)) {
                smallestDiff = diff;
                closest = item;
            }
        }
        return closest;
    }

    private getSubmissionTimestamp(submission: SubmissionVersion | ProgrammingSubmission | FileUploadSubmission): dayjs.Dayjs {
        return this.isSubmissionVersion(submission) ? (submission as SubmissionVersion).createdDate : (submission as Submission).submissionDate!;
    }

    private getSubmissionExercise(submission: SubmissionVersion | ProgrammingSubmission | FileUploadSubmission): Exercise | undefined {
        return this.isSubmissionVersion(submission) ? (submission as SubmissionVersion).submission.participation?.exercise : (submission as Submission).participation?.exercise;
    }

    private findExerciseIndex(firstSubmission: FileUploadSubmission | SubmissionVersion | ProgrammingSubmission) {
        const exercise = this.getSubmissionExercise(firstSubmission);
        return this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === exercise?.id);
    }

    protected readonly currentPageComponentsChanges = toObservable(this.currentPageComponents);
}
