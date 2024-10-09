import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { TranslateModule } from '@ngx-translate/core';
import { ReactiveFormsModule } from '@angular/forms';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exam } from 'app/entities/exam/exam.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StudentExamDetailTableRowComponent } from 'app/exam/manage/student-exams/student-exam-detail-table-row/student-exam-detail-table-row.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { MockTranslateValuesDirective } from '../../../../helpers/mocks/directive/mock-translate-values.directive';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faCheckDouble, faFileUpload, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { UMLDiagramType } from '@ls1intum/apollon';
import { provideRouter } from '@angular/router';

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
            imports: [NgbModule, NgxDatatableModule, ReactiveFormsModule, TranslateModule.forRoot()],
            declarations: [StudentExamDetailTableRowComponent, MockComponent(DataTableComponent), MockTranslateValuesDirective, MockPipe(ArtemisTranslatePipe)],
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
