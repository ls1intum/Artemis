import { Component, ContentChild, OnInit, TemplateRef } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { SubmissionPolicyService } from 'app/exercises/programming/manage/services/submission-policy.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { UMLModel } from '@ls1intum/apollon';
import { SafeHtml } from '@angular/platform-browser';
import { faAngleDown, faAngleUp, faBook, faEye, faFileSignature, faListAlt, faSignal, faTable, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ResultService } from 'app/exercises/shared/result/result.service';

@Component({
    selector: 'jhi-example-solution',
    templateUrl: './example-solution.component.html',
})
export class ExampleSolutionComponent implements OnInit {
    readonly AssessmentType = AssessmentType;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    readonly evaluateBadge = ResultService.evaluateBadge;
    readonly dayjs = dayjs;

    // private currentUser: User;
    private exerciseId: number;
    // public courseId: number;
    // public course: Course;
    public exercise?: Exercise;
    isExamExercise: boolean;
    exampleSolutionCollapsed: boolean;

    public modelingExercise?: ModelingExercise;
    public exampleSolution?: SafeHtml;
    public exampleSolutionUML?: UMLModel;
    public isProgrammingExerciseExampleSolutionPublished = false;

    // extension points, see shared/extension-point
    @ContentChild('overrideStudentActions') overrideStudentActions: TemplateRef<any>;

    /**
     * variables are only for testing purposes(noVersionControlAndContinuousIntegrationAvailable)
     */
    public inProductionEnvironment: boolean;

    // Icons
    faBook = faBook;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faListAlt = faListAlt;
    faSignal = faSignal;
    faFileSignature = faFileSignature;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

    constructor(
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private profileService: ProfileService,
        private alertService: AlertService,
        private programmingExerciseSubmissionPolicyService: SubmissionPolicyService,
        private artemisMarkdown: ArtemisMarkdownService,
        private courseService: CourseManagementService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const didExerciseChange = this.exerciseId !== parseInt(params['exerciseId'], 10);
            // const didCourseChange = this.courseId !== parseInt(params['courseId'], 10);
            this.exerciseId = parseInt(params['exerciseId'], 10);
            // this.courseId = parseInt(params['courseId'], 10);
            // this.courseService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));
            // this.accountService.identity().then((user: User) => {
            //     this.currentUser = user;
            // });
            if (didExerciseChange) {
                this.loadExercise();
            }
        });

        // Checks if the current environment is production
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProductionEnvironment = profileInfo.inProduction;
            }
        });
    }

    loadExercise() {
        this.exercise = undefined;
        this.exerciseService.getExerciseForExampleSolution(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.handleNewExercise(exerciseResponse.body!);
        });
    }

    handleNewExercise(newExercise: Exercise) {
        this.exercise = newExercise;

        this.showIfExampleSolutionPresent(newExercise);
    }

    /**
     * Sets example solution and related fields if exampleSolution exists on newExercise,
     * otherwise clears the previously set example solution related fields.
     *
     * @param newExercise Exercise model that may have an exampleSolution.
     */
    showIfExampleSolutionPresent(newExercise: Exercise) {
        // Clear fields below to avoid displaying old data if this method is called more than once.
        this.modelingExercise = undefined;
        this.exampleSolution = undefined;
        this.exampleSolutionUML = undefined;
        this.isProgrammingExerciseExampleSolutionPublished = false;

        if (newExercise.type === ExerciseType.MODELING) {
            this.modelingExercise = newExercise as ModelingExercise;
            if (this.modelingExercise.exampleSolutionModel) {
                this.exampleSolutionUML = JSON.parse(this.modelingExercise.exampleSolutionModel);
            }
        } else if (newExercise.type === ExerciseType.TEXT || newExercise.type === ExerciseType.FILE_UPLOAD) {
            const exercise = newExercise as TextExercise & FileUploadExercise;
            if (exercise.exampleSolution) {
                this.exampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(exercise.exampleSolution);
            }
        } else if (newExercise.type === ExerciseType.PROGRAMMING) {
            const exercise = newExercise as ProgrammingExercise;
            this.isProgrammingExerciseExampleSolutionPublished = exercise.exampleSolutionPublished || false;
        }
        // For TAs the example solution is collapsed on default to avoid spoiling, as the example solution is always shown to TAs
        this.exampleSolutionCollapsed = !!this.exercise?.isAtLeastTutor;
    }

    private onError(error: string) {
        this.alertService.error(error);
    }

    /**
     * Used to change the boolean value for the example solution dropdown menu
     */
    changeExampleSolution() {
        this.exampleSolutionCollapsed = !this.exampleSolutionCollapsed;
    }
}
