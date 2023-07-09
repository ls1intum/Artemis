import { AfterViewInit, ChangeDetectorRef, Component, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamPage } from 'app/entities/exam-page.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import dayjs from 'dayjs/esm';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { Observable, map, merge, mergeMap, toArray } from 'rxjs';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Submission } from 'app/entities/submission.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChangeContext, Options, SliderComponent } from 'ngx-slider-v2';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';

@Component({
    selector: 'jhi-student-exam-timeline',
    templateUrl: './student-exam-timeline.component.html',
    styleUrls: ['./student-exam-timeline.component.scss'],
})
export class StudentExamTimelineComponent implements OnInit, AfterViewInit {
    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILEUPLOAD = ExerciseType.FILE_UPLOAD;
    // determines if component was once drawn visited
    pageComponentVisited: boolean[];
    value: number;
    options: Options = {
        showTicks: true,
        stepsArray: [{ value: 0 }],
        // ticksTooltip: (value: number): string => {
        //     return this.datePipe.transform(value, 'time', true);
        // },
        ticksValuesTooltip: (value: number): string => {
            return this.datePipe.transform(value, 'time', true);
        },
        translate: (value: number): string => {
            return this.datePipe.transform(value, 'time', true);
        },
    };

    studentExam: StudentExam;
    exerciseIndex: number;
    activeExamPage = new ExamPage();
    courseId: number;
    submissionTimeStamps: dayjs.Dayjs[] = [];
    submissionVersions: SubmissionVersion[] = [];
    programmingSubmissions: ProgrammingSubmission[] = [];
    fileUploadSubmissions: FileUploadSubmission[] = [];
    currentExercise: Exercise | undefined;
    currentSubmission: SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined;
    @ViewChildren(ExamSubmissionComponent) currentPageComponents: QueryList<ExamSubmissionComponent>;
    @ViewChild('examNavigationBar') examNavigationBarComponent: ExamNavigationBarComponent;
    @ViewChild('slider') slider: SliderComponent;
    readonly SubmissionVersion = SubmissionVersion;

    constructor(
        private studentExamService: StudentExamService,
        private activatedRoute: ActivatedRoute,
        private submissionService: SubmissionService,
        private datePipe: ArtemisDatePipe,
        private changeDetectorRef: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ studentExam: studentExamWithGrade }) => {
            this.studentExam = studentExamWithGrade.studentExam;
            this.courseId = this.studentExam.exam?.course?.id!;
        });
        this.exerciseIndex = 0;
        this.pageComponentVisited = new Array(this.studentExam.exercises!.length).fill(false);
        this.retrieveSubmissionDataAndTimeStamps().subscribe((results) => {
            results.forEach((result) => {
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
            const firstSubmission = this.findFirstSubmissionForFirstExercise(this.studentExam.exercises![this.exerciseIndex]);
            this.currentSubmission = firstSubmission;
            this.examNavigationBarComponent.changePage(false, this.exerciseIndex, false, firstSubmission);
        });
    }

    ngAfterViewInit(): void {
        this.currentPageComponents.changes.subscribe(() => {
            const activeComponent = this.activePageComponent;
            if (activeComponent) {
                if (this.currentExercise?.type === ExerciseType.PROGRAMMING) {
                    this.updateProgrammingExerciseView();
                } else if (this.currentExercise?.type === ExerciseType.FILE_UPLOAD) {
                    activeComponent!.submission = this.currentSubmission as FileUploadSubmission;
                    activeComponent?.updateViewFromSubmission();
                } else {
                    activeComponent?.setSubmissionVersion(this.currentSubmission as SubmissionVersion);
                }
            }
        });
    }

    private updateProgrammingExerciseView() {
        const activeProgrammingComponent = this.activePageComponent as ProgrammingExamSubmissionComponent;
        activeProgrammingComponent!.studentParticipation.submissions![0] = this.currentSubmission as ProgrammingSubmission;
        activeProgrammingComponent.codeEditorContainer.aceEditor.fileSession = {};
        activeProgrammingComponent?.updateExamTimelineView();
    }

    private setupRangeSlider() {
        this.value = this.submissionTimeStamps[0]?.toDate().getTime() ?? 0;
        const newOptions: Options = Object.assign({}, this.options);
        newOptions.stepsArray = this.submissionTimeStamps.map((date) => {
            return {
                value: date.toDate().getTime(),
            };
        });
        this.options = newOptions;
    }

    private isSubmissionVersion(object: SubmissionVersion | Submission | undefined) {
        if (!object) {
            return false;
        }
        const submissionVersion = object as SubmissionVersion;
        return submissionVersion.id && submissionVersion.createdDate && submissionVersion.content && submissionVersion.submission;
    }

    retrieveSubmissionDataAndTimeStamps() {
        const submissionObservables: Observable<SubmissionVersion[] | Submission[]>[] = [];
        this.studentExam.exercises?.forEach((exercise) => {
            if (exercise.type === this.PROGRAMMING) {
                submissionObservables.push(this.submissionService.findAllSubmissionsOfParticipation(exercise.studentParticipations![0].id!).pipe(map(({ body }) => body!)));
            } else if (exercise.type === this.FILEUPLOAD) {
                submissionObservables.push(this.submissionService.findAllSubmissionsOfParticipation(exercise.studentParticipations![0].id!).pipe(map(({ body }) => body!)));
            } else {
                submissionObservables.push(
                    this.submissionService.findAllSubmissionVersionsOfSubmission(exercise.studentParticipations![0].submissions![0].id!).pipe(
                        mergeMap((versions) => versions),
                        toArray(),
                    ),
                );
            }
        });
        const returnObservable = merge(...submissionObservables);
        return returnObservable;
    }

    private sortTimeStamps() {
        this.submissionTimeStamps = this.submissionTimeStamps.sort((date1, date2) => (date1.isAfter(date2) ? 1 : -1));
    }

    onPageChange(exerciseChange: {
        overViewChange: boolean;
        exercise?: Exercise;
        forceSave: boolean;
        submission?: ProgrammingSubmission | SubmissionVersion | FileUploadSubmission;
        initial?: boolean;
    }): void {
        const activeComponent = this.activePageComponent;
        if (activeComponent) {
            activeComponent.onDeactivate();
        }

        if (!exerciseChange.submission) {
            exerciseChange.submission = this.findSubmissionForExerciseClosestToCurrentTimeStampForExercise(exerciseChange.exercise!);
        }

        if (this.isSubmissionVersion(exerciseChange.submission)) {
            const submissionVersion = exerciseChange.submission as SubmissionVersion;
            this.value = submissionVersion.createdDate.toDate().getTime();
        } else if (this.isFileUploadSubmission(exerciseChange.submission)) {
            const fileUploadSubmission = exerciseChange.submission as FileUploadSubmission;
            this.value = fileUploadSubmission.submissionDate!.toDate().getTime();
        } else {
            const programmingSubmission = exerciseChange.submission as ProgrammingSubmission;
            this.value = programmingSubmission.submissionDate!.toDate().getTime();
        }
        this.currentSubmission = exerciseChange.submission;
        this.initializeExercise(exerciseChange.exercise!, exerciseChange.submission);
    }

    private findFirstSubmissionForFirstExercise(exercise: Exercise): FileUploadSubmission | SubmissionVersion | ProgrammingSubmission | undefined {
        if (exercise.type === this.PROGRAMMING) {
            return this.programmingSubmissions.find((submission) => submission.submissionDate?.isSame(this.submissionTimeStamps[0]));
        } else if (exercise.type === this.FILEUPLOAD) {
            return this.fileUploadSubmissions.find((submission) => submission.submissionDate?.isSame(this.submissionTimeStamps[0]));
        } else {
            return this.submissionVersions.find((submission) => submission.createdDate.isSame(this.submissionTimeStamps[0]));
        }
    }

    initializeExercise(exercise: Exercise, submission: Submission | SubmissionVersion | undefined) {
        this.activeExamPage.exercise = exercise;
        // set current exercise Index
        this.exerciseIndex = this.studentExam.exercises!.findIndex((exercise1) => exercise1.id === exercise.id);
        this.activateActiveComponent();
        const activeComponent = this.activePageComponent;
        this.currentSubmission = submission;
        if (activeComponent) {
            if (this.currentExercise?.type === ExerciseType.PROGRAMMING) {
                this.updateProgrammingExerciseView();
            } else if (this.currentExercise?.type === ExerciseType.FILE_UPLOAD) {
                activeComponent!.submission = submission as FileUploadSubmission;
                activeComponent?.updateViewFromSubmission();
            } else {
                activeComponent!.submissionVersion = submission as SubmissionVersion;
                activeComponent?.setSubmissionVersion(submission as SubmissionVersion);
            }
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

    get activePageComponent(): ExamSubmissionComponent | undefined {
        // we have to find the current component based on the activeExercise because the queryList might not be full yet (e.g. only 2 of 5 components initialized)
        return this.currentPageComponents.find((submissionComponent) => (submissionComponent as ExamSubmissionComponent).getExercise().id === this.activeExamPage.exercise?.id);
    }

    onInputChange(changeContext: ChangeContext) {
        this.value = changeContext.value;
        console.log('change');
        const submission = this.findCorrespondingSubmissionForTimestamp(changeContext.value);
        console.log(submission);
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

        this.examNavigationBarComponent.changePage(false, exerciseIndex, false, submission);
    }

    private findCorrespondingSubmissionForTimestamp(timestamp: number): SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined {
        console.log('find submission for timestamp' + timestamp);
        for (let i = 0; i < this.submissionVersions.length; i++) {
            console.log(timestamp);
            const comparisonObject = dayjs(timestamp);
            const submissionVersion = this.submissionVersions[i];
            if (submissionVersion.createdDate.isSame(comparisonObject)) {
                return submissionVersion;
            }
        }
        for (let i = 0; i < this.programmingSubmissions.length; i++) {
            const comparisonObject = dayjs(timestamp);
            const programmingSubmission = this.programmingSubmissions[i];
            if (programmingSubmission.submissionDate?.isSame(comparisonObject)) {
                return programmingSubmission;
            }
        }
        for (let i = 0; i < this.fileUploadSubmissions.length; i++) {
            const comparisonObject = dayjs(timestamp);
            const fileUploadSubmission = this.fileUploadSubmissions[i];
            if (fileUploadSubmission.submissionDate?.isSame(comparisonObject)) {
                return fileUploadSubmission;
            }
        }
        return undefined;
    }

    private isFileUploadSubmission(object: FileUploadSubmission | SubmissionVersion | ProgrammingSubmission | undefined) {
        if (!object) {
            return false;
        }
        const fileUploadSubmission = object as FileUploadSubmission;
        return !!fileUploadSubmission.id && fileUploadSubmission.submissionDate && fileUploadSubmission.filePath;
    }

    private findSubmissionForExerciseClosestToCurrentTimeStampForExercise(exercise: Exercise) {
        const comparisonObject = dayjs(this.value);
        console.log('find submission for exercise closest to current timestamp');
        console.log(comparisonObject);
        let smallestDiff = Number.MAX_VALUE;
        let timestampWithSmallestDiff = 0;
        if (exercise.type === ExerciseType.PROGRAMMING) {
            timestampWithSmallestDiff = this.findClosestTimestampForExerciseInSubmissionArray(exercise, this.programmingSubmissions);
        }
        if (exercise.type === ExerciseType.FILE_UPLOAD) {
            // file upload exercises only have one submission
            return this.fileUploadSubmissions.find((submission) => submission.participation?.exercise?.id === exercise.id);
        } else {
            for (let i = 0; i < this.submissionVersions.length; i++) {
                if (
                    Math.abs(this.submissionVersions[i].createdDate.diff(comparisonObject)) < smallestDiff &&
                    !this.submissionVersions[i].createdDate.isSame(comparisonObject) &&
                    this.submissionVersions[i]?.submission.participation?.exercise?.id === exercise.id
                ) {
                    smallestDiff = Math.abs(this.submissionVersions[i].createdDate.diff(comparisonObject));
                    timestampWithSmallestDiff = this.submissionVersions[i].createdDate.valueOf();
                }
            }
        }
        console.log(dayjs(timestampWithSmallestDiff));
        return this.findCorrespondingSubmissionForTimestamp(timestampWithSmallestDiff);
    }

    private findClosestTimestampForExerciseInSubmissionArray(exercise: Exercise, submissions: ProgrammingSubmission[] | FileUploadSubmission[]) {
        const comparisonObject = dayjs(this.value);
        let smallestDiff = Number.MAX_VALUE;
        let timestampWithSmallestDiff = 0;
        for (let i = 0; i < submissions.length; i++) {
            if (
                submissions[i].submissionDate!.diff(comparisonObject) < smallestDiff &&
                !submissions[i].submissionDate?.isSame(comparisonObject) &&
                this.fileUploadSubmissions[i]?.participation?.exercise === exercise
            ) {
                smallestDiff = submissions[i].submissionDate!.diff(comparisonObject);
                timestampWithSmallestDiff = this.fileUploadSubmissions[i].submissionDate!.valueOf();
            }
        }
        return timestampWithSmallestDiff;
    }
}
