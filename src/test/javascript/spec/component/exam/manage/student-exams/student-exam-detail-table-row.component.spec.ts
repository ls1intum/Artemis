import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faCheckDouble, faFileUpload, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Result } from 'app/entities/result.model';
import { StudentExamDetailTableRowComponent } from 'app/exam/manage/student-exams/student-exam-detail-table-row/student-exam-detail-table-row.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MockTranslateValuesDirective } from '../../../../helpers/mocks/directive/mock-translate-values.directive';

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
        studentParticipation.results = [result];
        exercise = new ModelingExercise(UMLDiagramType.ActivityDiagram, course, new ExerciseGroup());
        exercise.maxPoints = 100;
        exercise.studentParticipations = [studentParticipation];

        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), NgbModule, NgxDatatableModule, FontAwesomeTestingModule, ReactiveFormsModule, TranslateModule.forRoot()],
            declarations: [StudentExamDetailTableRowComponent, MockComponent(DataTableComponent), MockTranslateValuesDirective, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(AlertService), MockDirective(TranslateDirective)],
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

    it('should route to programming submission dashboard', () => {
        const getAssessmentLinkSpy = jest.spyOn(studentExamDetailTableRowComponent, 'getAssessmentLink');
        studentExamDetailTableRowComponentFixture.detectChanges();
        studentExamDetailTableRowComponent.courseId = 23;
        studentExamDetailTableRowComponent.examId = exam1.id!;

        const programmingExercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            id: 12,
            exerciseGroup: { id: 13 },
            type: ExerciseType.PROGRAMMING,
        };
        const route = studentExamDetailTableRowComponent.getAssessmentLink(programmingExercise);
        expect(getAssessmentLinkSpy).toHaveBeenCalledOnce();
        expect(route).toEqual(['/course-management', '23', 'exams', '1', 'exercise-groups', '13', 'programming-exercises', '12', 'submissions']);
    });

    it('should route to modeling submission', () => {
        const getAssessmentLinkSpy = jest.spyOn(studentExamDetailTableRowComponent, 'getAssessmentLink');
        studentExamDetailTableRowComponentFixture.detectChanges();
        studentExamDetailTableRowComponent.courseId = 23;
        studentExamDetailTableRowComponent.examId = exam1.id!;
        const modelingExercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            id: 12,
            type: ExerciseType.MODELING,
            exerciseGroup: { id: 12 },
        };
        const submission = { id: 14 };
        const route = studentExamDetailTableRowComponent.getAssessmentLink(modelingExercise, submission);
        expect(getAssessmentLinkSpy).toHaveBeenCalledOnce();
        expect(route).toEqual(['/course-management', '23', 'exams', '1', 'exercise-groups', '12', 'modeling-exercises', '12', 'submissions', '14', 'assessment']);
    });
});
