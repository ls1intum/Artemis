import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { TranslateModule } from '@ngx-translate/core';
import { ReactiveFormsModule } from '@angular/forms';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StudentExamDetailTableRowComponent } from 'app/exam/manage/student-exams/student-exam-detail-table-row/student-exam-detail-table-row.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faCheckDouble, faFileUpload, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { UMLDiagramType } from '@ls1intum/apollon';
import { provideRouter } from '@angular/router';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('StudentExamDetailTableRowComponent', () => {
    let studentExamDetailTableRowComponentFixture: ComponentFixture<StudentExamDetailTableRowComponent>;
    let studentExamDetailTableRowComponent: StudentExamDetailTableRowComponent;
    let course: Course;
    let exercise: Exercise;
    let exam1: Exam;
    let studentParticipation: StudentParticipation;
    let result: Result;

    beforeEach(() => {
        course = { id: 1 };
        exam1 = { course, id: 1 };
        result = { score: 40, id: 10 };
        studentParticipation = new StudentParticipation(ParticipationType.STUDENT);
        const submission = new TextSubmission();
        submission.results = [result];
        submission.participation = studentParticipation;
        studentParticipation.submissions = [submission];
        exercise = new ModelingExercise(UMLDiagramType.ActivityDiagram, course, new ExerciseGroup());
        exercise.maxPoints = 100;
        exercise.studentParticipations = [studentParticipation];

        return TestBed.configureTestingModule({
            imports: [NgbModule, NgxDatatableModule, ReactiveFormsModule, TranslateModule.forRoot(), FaIconComponent, StudentExamDetailTableRowComponent],
            declarations: [MockComponent(DataTableComponent), MockTranslateValuesDirective, MockPipe(ArtemisTranslatePipe)],
            providers: [provideRouter([]), MockProvider(AlertService), MockDirective(TranslateDirective)],
        })
            .compileComponents()
            .then(() => {
                studentExamDetailTableRowComponentFixture = TestBed.createComponent(StudentExamDetailTableRowComponent);
                studentExamDetailTableRowComponent = studentExamDetailTableRowComponentFixture.componentInstance;
            });
    });
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should return the right icon based on exercise type', () => {
        exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, new ExerciseGroup());
        expect(studentExamDetailTableRowComponent.getIcon(exercise.type!)).toEqual(faProjectDiagram);

        exercise = new ProgrammingExercise(course, new ExerciseGroup());
        expect(studentExamDetailTableRowComponent.getIcon(exercise.type!)).toEqual(faKeyboard);

        exercise = new QuizExercise(course, new ExerciseGroup());
        expect(studentExamDetailTableRowComponent.getIcon(exercise.type!)).toEqual(faCheckDouble);

        exercise = new FileUploadExercise(course, new ExerciseGroup());
        expect(studentExamDetailTableRowComponent.getIcon(exercise.type!)).toEqual(faFileUpload);
    });

    it('should route to modeling submission', () => {
        const getAssessmentLinkSpy = jest.spyOn(studentExamDetailTableRowComponent, 'getAssessmentLink');
        const modelingExercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            id: 12,
            type: ExerciseType.MODELING,
            exerciseGroup: { id: 12 },
        };
        studentExamDetailTableRowComponentFixture.componentRef.setInput('exercise', modelingExercise);
        studentExamDetailTableRowComponentFixture.componentRef.setInput('examId', exam1.id!);
        studentExamDetailTableRowComponentFixture.componentRef.setInput('isTestRun', false);
        studentExamDetailTableRowComponentFixture.componentRef.setInput('course', course);
        studentExamDetailTableRowComponentFixture.componentRef.setInput('busy', false);
        studentExamDetailTableRowComponentFixture.componentRef.setInput('studentExam', {} as StudentExam);
        studentExamDetailTableRowComponentFixture.componentRef.setInput('achievedPointsPerExercise', { 1: 1 });
        studentExamDetailTableRowComponentFixture.detectChanges();
        studentExamDetailTableRowComponent.courseId = 23;

        const submission = { id: 14 };
        const route = studentExamDetailTableRowComponent.getAssessmentLink(modelingExercise, submission);
        expect(getAssessmentLinkSpy).toHaveBeenCalledOnce();
        expect(route).toEqual(['/course-management', '23', 'exams', '1', 'exercise-groups', '12', 'modeling-exercises', '12', 'submissions', '14', 'assessment']);
    });
});
