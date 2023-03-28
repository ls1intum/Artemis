import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { MockParticipationService } from '../../helpers/mocks/service/mock-participation.service';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProblemStatementComponent } from 'app/overview/exercise-details/problem-statement/problem-statement.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { MockComponent } from 'ng-mocks';

describe('ProblemStatementComponent', () => {
    let component: ProblemStatementComponent;
    let fixture: ComponentFixture<ProblemStatementComponent>;
    let mockActivatedRoute: any;

    beforeEach(() => {
        mockActivatedRoute = {
            params: of({ exerciseId: '1', participationId: '2' }),
        };

        TestBed.configureTestingModule({
            declarations: [ProblemStatementComponent, TranslatePipeMock, HtmlForMarkdownPipe, MockComponent(ProgrammingExerciseInstructionComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                { provide: ExerciseService, useValue: MockExerciseService },
                { provide: ParticipationService, useValue: MockParticipationService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProblemStatementComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render problem statement when exercise is available', () => {
        const course = new Course();
        course.id = 123;
        const exercise = new TextExercise(course, undefined);
        exercise.problemStatement = 'Test problem statement';
        course.exercises = [exercise];
        component.exercise = exercise;
        fixture.detectChanges();
        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('#problem-statement')).toBeTruthy();
        expect(compiled.querySelector('#problem-statement p').innerHTML).toContain('Test problem statement');
    });

    it('should render programming exercise instructions when exercise is a programming exercise and participation and exercise are available', () => {
        const course = new Course();
        course.id = 123;
        const exercise = new ProgrammingExercise(course, undefined);
        exercise.problemStatement = 'Test problem statement';
        course.exercises = [exercise];

        const participation: StudentParticipation = {} as StudentParticipation;
        component.exercise = exercise;
        component.participation = participation;
        fixture.detectChanges();
        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('jhi-programming-exercise-instructions')).toBeTruthy();
    });

    it('should not render programming exercise instructions when exercise is not a programming exercise', () => {
        const course = new Course();
        course.id = 123;
        const exercise = new TextExercise(course, undefined);
        exercise.problemStatement = 'Test problem statement';
        course.exercises = [exercise];

        const participation: StudentParticipation = {} as StudentParticipation;
        component.exercise = exercise;
        component.participation = participation;
        fixture.detectChanges();
        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('jhi-programming-exercise-instructions')).toBeFalsy();
    });
});
