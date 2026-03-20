import { Component, computed, input, viewChild } from '@angular/core';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { faAlignLeft, faComment, faGear, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { ProblemStatementComponent } from 'app/core/course/overview/exercise-details/problem-statement/problem-statement.component';
import { DiscussionFeedComponent } from 'app/communication/shared/discussion-section/discussion-feed.component';
import { TextEditorComponent } from 'app/text/overview/text-editor/text-editor.component';
import { CodeEditorStudentContainerComponent } from 'app/programming/overview/code-editor-student-container/code-editor-student-container.component';
import { ModelingSubmissionComponent } from 'app/modeling/overview/modeling-submission/modeling-submission.component';
import { FileUploadSubmissionComponent } from 'app/fileupload/overview/file-upload-submission/file-upload-submission.component';
import { QuizParticipationComponent } from 'app/quiz/overview/participation/quiz-participation.component';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared/components/resizable-panels/resizable-panels.component';

@Component({
    selector: 'jhi-exercise-split-panel',
    templateUrl: './exercise-split-panel.component.html',
    imports: [
        ResizablePanelsComponent,
        PanelDirective,
        ProblemStatementComponent,
        DiscussionFeedComponent,
        TextEditorComponent,
        CodeEditorStudentContainerComponent,
        ModelingSubmissionComponent,
        FileUploadSubmissionComponent,
        QuizParticipationComponent,
    ],
})
export class ExerciseSplitPanelComponent {
    protected readonly faGear = faGear;
    protected readonly faComment = faComment;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faAlignLeft = faAlignLeft;
    protected readonly getIcon = getIcon;
    protected readonly ExerciseType = ExerciseType;

    readonly exercise = input.required<Exercise>();
    readonly studentParticipation = input<StudentParticipation>();

    private readonly textEditor = viewChild(TextEditorComponent);
    private readonly codeEditor = viewChild(CodeEditorStudentContainerComponent);
    private readonly modelingSubmission = viewChild(ModelingSubmissionComponent);
    private readonly fileUploadSubmission = viewChild(FileUploadSubmissionComponent);
    private readonly quizParticipation = viewChild(QuizParticipationComponent);

    readonly showDiscussion = computed(() => {
        const course = this.exercise().course;
        return !!course && (isCommunicationEnabled(course) || isMessagingEnabled(course));
    });

    readonly showCodeEditor = computed(() => {
        const exercise = this.exercise();
        return exercise.type === ExerciseType.PROGRAMMING && (exercise as ProgrammingExercise).allowOnlineEditor;
    });

    readonly canSubmit = computed(() => {
        if (!this.studentParticipation()) return false;
        const type = this.exercise().type;
        if (type === ExerciseType.QUIZ) {
            return (this.exercise() as QuizExercise).quizStarted ?? false;
        }
        if (type === ExerciseType.PROGRAMMING) {
            return (this.exercise() as ProgrammingExercise).allowOnlineEditor ?? false;
        }
        return type === ExerciseType.TEXT || type === ExerciseType.MODELING || type === ExerciseType.FILE_UPLOAD;
    });

    submitExercise(): void {
        this.textEditor()?.submit();
        this.codeEditor()?.commit();
        this.modelingSubmission()?.submit();
        this.fileUploadSubmission()?.submitExercise();
        this.quizParticipation()?.onSubmit();
    }
}
