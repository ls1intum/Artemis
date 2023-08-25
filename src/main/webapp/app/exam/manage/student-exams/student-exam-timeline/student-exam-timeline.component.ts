import { AfterViewInit, Component, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamPage } from 'app/entities/exam-page.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import dayjs from 'dayjs/esm';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { Observable, Subscription, forkJoin, map, mergeMap, toArray } from 'rxjs';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Submission } from 'app/entities/submission.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChangeContext, Options, SliderComponent } from 'ngx-slider-v2';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { SubmissionVersionService } from 'app/exercises/shared/submission-version/submission-version.service';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';

@Component({
    selector: 'jhi-student-exam-timeline',
    templateUrl: './student-exam-timeline.component.html',
})
export class StudentExamTimelineComponent implements OnInit, AfterViewInit {
    readonly ExerciseType = ExerciseType;
    readonly SubmissionVersion = SubmissionVersion;

    // determines if component was once drawn visited
    pageComponentVisited: boolean[];
    selectedTimestamp: number;
    options: Options = {
        showTicks: true,
        stepsArray: [{ value: 0 }],
        translate: (value: number): string => {
            return this.datePipe.transform(value, 'time', true);
        },
    };

    studentExam: StudentExam;
    exerciseIndex: number;
    activeExamPage = new ExamPage();
    submissionTimeStamps: dayjs.Dayjs[] = [];
    submissionVersions: SubmissionVersion[] = [];
    programmingSubmissions: ProgrammingSubmission[] = [];
    fileUploadSubmissions: FileUploadSubmission[] = [];

    commitInfosPerExercise: Map<Exercise, CommitInfo[]> = new Map<Exercise, CommitInfo[]>();
    currentExercise: Exercise | undefined;
    currentSubmission: SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined;
    changesSubscription: Subscription;
    @ViewChildren(ExamSubmissionComponent) currentPageComponents: QueryList<ExamSubmissionComponent>;
    @ViewChild('examNavigationBar') examNavigationBarComponent: ExamNavigationBarComponent;
    @ViewChild('slider') slider: SliderComponent;

    constructor(
        private activatedRoute: ActivatedRoute,
        private submissionService: SubmissionService,
        private submissionVersionService: SubmissionVersionService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private datePipe: ArtemisDatePipe,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ studentExam: studentExamWithGrade }) => {
            this.studentExam = studentExamWithGrade.studentExam;
            // this.retrieveCommitInfos();
        });
        this.exerciseIndex = 0;
        this.pageComponentVisited = new Array(this.studentExam.exercises!.length).fill(false);
        this.retrieveSubmissionDataAndTimeStamps().subscribe((results) => {
            const allSubmissions = results.flat();
            allSubmissions.forEach((result) => {
                //workaround because instanceof does not work.
                if (this.isSubmissionVersion(result)) {
                    const submissionVersion = result as SubmissionVersion;
                    this.submissionVersions.push(submissionVersion);
                    this.submissionTimeStamps.push(submissionVersion.createdDate);
                } else if (this.isFileUploadSubmission(result)) {
                    const fileUploadSubmission = result as FileUploadSubmission;
                    this.fileUploadSubmissions.push(fileUploadSubmission);
                    this.submissionTimeStamps.push(fileUploadSubmission.submissionDate!);
                } else {
                    const programmingSubmission = result as ProgrammingSubmission;
                    this.programmingSubmissions.push(programmingSubmission);
                    this.submissionTimeStamps.push(programmingSubmission.submissionDate!);
                }
            });
            this.sortTimeStamps();
            this.setupRangeSlider();
            const firstSubmission = this.findFirstSubmission();
            this.currentSubmission = firstSubmission;
            this.exerciseIndex = this.findExerciseIndex(firstSubmission!);
            this.examNavigationBarComponent.changePage(false, this.exerciseIndex, false, firstSubmission);
        });
    }

    ngAfterViewInit(): void {
        this.changesSubscription = this.currentPageComponents.changes.subscribe(() => {
            this.updateSubmissionOrSubmissionVersionInView();
        });
    }

    private updateProgrammingExerciseView() {
        const activeProgrammingComponent = this.activePageComponent as ProgrammingExerciseExamDiffComponent;
        if (activeProgrammingComponent) {
            activeProgrammingComponent.studentParticipation = this.currentExercise!.studentParticipations![0];
            activeProgrammingComponent.exercise = this.currentExercise!;
            activeProgrammingComponent.currentSubmission = this.currentSubmission as ProgrammingSubmission;
            activeProgrammingComponent.previousSubmission = this.findPreviousSubmission(this.currentExercise!, this.currentSubmission!);
            activeProgrammingComponent.loadGitDiffReport();
        }
    }

    private findPreviousSubmission(currentExercise: Exercise, currentSubmission: ProgrammingSubmission): ProgrammingSubmission | undefined {
        const comparisonTimestamp = currentSubmission.submissionDate;
        let smallestDiff = Number.MAX_VALUE;
        let correspondingSubmission: ProgrammingSubmission | undefined;
        for (let i = 0; i < this.programmingSubmissions.length; i++) {
            const submission = this.programmingSubmissions[i];
            if (
                submission.submissionDate!.isBefore(comparisonTimestamp) &&
                submission.submissionDate!.diff(comparisonTimestamp) < smallestDiff &&
                submission.participation?.exercise?.id === currentExercise.id
            ) {
                smallestDiff = submission.submissionDate!.diff(comparisonTimestamp);
                correspondingSubmission = submission;
            }
        }
        return correspondingSubmission;
    }

    private setupRangeSlider() {
        this.selectedTimestamp = this.submissionTimeStamps[0]?.toDate().getTime() ?? 0;
        const newOptions: Options = Object.assign({}, this.options);
        newOptions.stepsArray = this.submissionTimeStamps.map((date) => {
            return {
                value: date.toDate().getTime(),
            };
        });
        newOptions.ticksTooltip = (value: number): string => {
            return this.datePipe.transform(this.options.stepsArray?.at(value)?.value, 'time', true);
        };
        this.options = newOptions;
    }

    /**
     * Checks if the submission is a submission version
     * @param object the object to check
     */
    isSubmissionVersion(object: SubmissionVersion | Submission | undefined) {
        if (!object) {
            return false;
        }
        const submissionVersion = object as SubmissionVersion;
        return submissionVersion.id && submissionVersion.createdDate && submissionVersion.content && submissionVersion.submission;
    }

    /**
     * Retrieve all submission versions/submissions for all exercises of the exam
     */
    retrieveSubmissionDataAndTimeStamps() {
        const submissionObservables: Observable<SubmissionVersion[] | Submission[]>[] = [];
        this.studentExam.exercises?.forEach((exercise) => {
            if (exercise.type === ExerciseType.PROGRAMMING) {
                const id = exercise.studentParticipations![0].id!;
                const programmingSubmission = this.submissionService.findAllSubmissionsOfParticipation(id).pipe(map(({ body }) => body!));
                submissionObservables.push(programmingSubmission);
            } else if (exercise.type === ExerciseType.FILE_UPLOAD) {
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
        return forkJoin(...submissionObservables);
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
    onPageChange(exerciseChange: {
        overViewChange: boolean;
        exercise?: Exercise;
        forceSave: boolean;
        submission?: ProgrammingSubmission | SubmissionVersion | FileUploadSubmission;
    }): void {
        const activeComponent = this.activePageComponent;
        if (activeComponent) {
            activeComponent.onDeactivate();
        }

        if (!exerciseChange.submission) {
            // only change the submission if the exercise has changed
            if (exerciseChange.exercise !== this.currentExercise) {
                exerciseChange.submission = this.findSubmissionForExerciseClosestToCurrentTimeStampForExercise(exerciseChange.exercise!);
            }
        }

        if (exerciseChange.submission) {
            if (this.isSubmissionVersion(exerciseChange.submission)) {
                const submissionVersion = exerciseChange.submission as SubmissionVersion;
                this.selectedTimestamp = submissionVersion.createdDate.toDate().getTime();
            } else if (this.isFileUploadSubmission(exerciseChange.submission)) {
                const fileUploadSubmission = exerciseChange.submission as FileUploadSubmission;
                this.selectedTimestamp = fileUploadSubmission.submissionDate!.toDate().getTime();
            } else {
                const programmingSubmission = exerciseChange.submission as ProgrammingSubmission;
                this.selectedTimestamp = programmingSubmission.submissionDate!.toDate().getTime();
            }
            this.currentSubmission = exerciseChange.submission;
            this.initializeExercise(exerciseChange.exercise!, exerciseChange.submission);
        }
    }

    private findFirstSubmission(): FileUploadSubmission | SubmissionVersion | ProgrammingSubmission | undefined {
        const submissionVersion = this.submissionVersions.find((submission) => submission.createdDate.isSame(this.submissionTimeStamps[0]));
        if (!submissionVersion) {
            const programmingSubmission = this.programmingSubmissions.find((submission) => submission.submissionDate?.isSame(this.submissionTimeStamps[0]));
            if (!programmingSubmission) {
                return this.fileUploadSubmissions.find((submission) => submission.submissionDate?.isSame(this.submissionTimeStamps[0]));
            } else {
                return programmingSubmission;
            }
        }
        return submissionVersion;
    }

    initializeExercise(exercise: Exercise, submission: Submission | SubmissionVersion | undefined) {
        this.activeExamPage.exercise = exercise;
        // set current exercise Index
        this.exerciseIndex = this.studentExam.exercises!.findIndex((exercise1) => exercise1.id === exercise.id);
        //TODO does this work or do we need to set these two properties after activating the component?
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
            const activePageComponent = this.activePageComponent as ExamSubmissionComponent;
            activePageComponent?.setSubmissionVersion(this.currentSubmission as SubmissionVersion);
        }
    }

    private updateFileUploadExerciseView() {
        const fileUploadComponent = this.activePageComponent as FileUploadExamSubmissionComponent;
        if (fileUploadComponent) {
            fileUploadComponent.studentSubmission = this.currentSubmission as FileUploadSubmission;
            fileUploadComponent.updateViewFromSubmission();
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
        return this.currentPageComponents.find((submissionComponent) => (submissionComponent as ExamSubmissionComponent).getExercise().id === this.activeExamPage.exercise?.id);
    }

    /**
     * This method is called when the user clicks on the slider
     * @param changeContext the change context of the slider
     */
    onInputChange(changeContext: ChangeContext) {
        this.selectedTimestamp = changeContext.value;
        const submission = this.findCorrespondingSubmissionForTimestamp(changeContext.value);
        if (this.isSubmissionVersion(submission)) {
            const submissionVersion = submission as SubmissionVersion;
            this.currentExercise = submissionVersion.submission.participation?.exercise;
        } else if (this.isFileUploadSubmission(submission)) {
            const fileUploadSubmission = submission as FileUploadSubmission;
            this.currentExercise = fileUploadSubmission.participation?.exercise;
        } else {
            const programmingSubmission = submission as ProgrammingSubmission;
            this.currentExercise = programmingSubmission.participation?.exercise;
        }
        const exerciseIndex = this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === this.currentExercise?.id);
        this.exerciseIndex = exerciseIndex;
        this.currentSubmission = submission;
        this.examNavigationBarComponent.changePage(false, exerciseIndex, false, submission);
    }

    /**
     * Finds the submission for the current timestamp.
     * @param timestamp The timestamp for which the submission should be found.
     */
    private findCorrespondingSubmissionForTimestamp(timestamp: number): SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined {
        const comparisonObject = dayjs(timestamp);
        for (let i = 0; i < this.submissionVersions.length; i++) {
            const submissionVersion = this.submissionVersions[i];
            if (submissionVersion.createdDate.isSame(comparisonObject)) {
                return submissionVersion;
            }
        }
        for (let i = 0; i < this.programmingSubmissions.length; i++) {
            const programmingSubmission = this.programmingSubmissions[i];
            if (programmingSubmission.submissionDate?.isSame(comparisonObject)) {
                return programmingSubmission;
            }
        }
        for (let i = 0; i < this.fileUploadSubmissions.length; i++) {
            const fileUploadSubmission = this.fileUploadSubmissions[i];
            if (fileUploadSubmission.submissionDate?.isSame(comparisonObject)) {
                return fileUploadSubmission;
            }
        }
        return undefined;
    }

    /**
     * helper method to check if the object is a file upload submission
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
     * Finds the submission for the exercise with the closest timestamp to the current timestamp.
     * @param exercise The exercise for which the submission should be found.
     */
    private findSubmissionForExerciseClosestToCurrentTimeStampForExercise(exercise: Exercise) {
        const comparisonObject = dayjs(this.selectedTimestamp);
        let smallestDiff = Number.MAX_VALUE;
        let timestampWithSmallestDiff = 0;
        if (exercise.type === ExerciseType.PROGRAMMING) {
            timestampWithSmallestDiff = this.findClosestTimestampForExerciseInSubmissionArray(exercise, this.programmingSubmissions);
        } else if (exercise.type === ExerciseType.FILE_UPLOAD) {
            // file upload exercises only have one submission
            return this.fileUploadSubmissions.find((submission) => submission.participation?.exercise?.id === exercise.id);
        } else {
            const numberOfSubmissionsForExercise = this.submissionVersions.filter((submission) => submission.submission?.participation?.exercise?.id === exercise.id).length;
            for (let i = 0; i < this.submissionVersions.length; i++) {
                if (
                    Math.abs(this.submissionVersions[i].createdDate.diff(comparisonObject)) < smallestDiff &&
                    this.submissionVersions[i]?.submission.participation?.exercise?.id === exercise.id &&
                    (!this.submissionVersions[i].createdDate.isSame(comparisonObject) || numberOfSubmissionsForExercise === 1)
                ) {
                    smallestDiff = Math.abs(this.submissionVersions[i].createdDate.diff(comparisonObject));
                    timestampWithSmallestDiff = this.submissionVersions[i].createdDate.valueOf();
                }
            }
        }
        return this.findCorrespondingSubmissionForTimestamp(timestampWithSmallestDiff);
    }

    /**
     * Finds the closest timestamp for a submission of a given exercise in a given submission array
     * @param exercise the exercise for which the submission should be found
     * @param submissions the submissions array in which the submission should be found
     */
    private findClosestTimestampForExerciseInSubmissionArray(exercise: Exercise, submissions: Submission[]): number {
        const comparisonObject = dayjs(this.selectedTimestamp);
        let smallestDiff = Number.MAX_VALUE;
        let timestampWithSmallestDiff = 0;
        const numberOfSubmissionsForExercise = submissions.filter(
            (submission: ProgrammingSubmission | FileUploadSubmission) => submission.participation?.exercise?.id === exercise.id,
        ).length;
        for (let i = 0; i < submissions.length; i++) {
            if (
                submissions[i].submissionDate!.diff(comparisonObject) < smallestDiff &&
                submissions[i]?.participation?.exercise?.id === exercise.id &&
                (!submissions[i].submissionDate?.isSame(comparisonObject) || numberOfSubmissionsForExercise === 1)
            ) {
                smallestDiff = submissions[i].submissionDate!.diff(comparisonObject);
                timestampWithSmallestDiff = submissions[i].submissionDate!.valueOf();
            }
        }
        return timestampWithSmallestDiff;
    }

    private findExerciseIndex(firstSubmission: FileUploadSubmission | SubmissionVersion | ProgrammingSubmission) {
        if (this.isSubmissionVersion(firstSubmission)) {
            const submissionVersion = firstSubmission as SubmissionVersion;
            return this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === submissionVersion.submission.participation?.exercise?.id);
        } else {
            const submission = firstSubmission as FileUploadSubmission | ProgrammingSubmission;
            return this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === submission.participation?.exercise?.id);
        }
    }
}
