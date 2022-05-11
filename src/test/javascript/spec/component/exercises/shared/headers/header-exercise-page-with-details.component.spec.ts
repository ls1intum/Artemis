import { TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { ArtemisTestModule } from '../../../../test.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ExerciseTypePipe } from 'app/shared/pipes/exercise-type.pipe';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

describe('HeaderExercisePageWithDetails', () => {
    let component: HeaderExercisePageWithDetailsComponent;

    let exam: Exam;
    let exercise: ProgrammingExercise;
    let participation: StudentParticipation;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                HeaderExercisePageWithDetailsComponent,
                MockComponent(DifficultyBadgeComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(NotReleasedTagComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ExerciseTypePipe),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                const fixture = TestBed.createComponent(HeaderExercisePageWithDetailsComponent);
                component = fixture.componentInstance;

                exercise = new ProgrammingExercise(undefined, undefined);
                component.exercise = exercise;

                exam = new Exam();
                participation = new StudentParticipation(ParticipationType.PROGRAMMING);
            });
    });

    it('should initialise badges, icons, and categories', () => {
        component.ngOnInit();

        expect(component.isExamMode).toBe(false);
        expect(component.exerciseCategories).toEqual([]);
        expect(component.exerciseStatusBadge).toBe('bg-success');
        // @ts-ignore
        expect(component.icon.iconName).toBe('keyboard');

        // dueDate, categories, examMode should also be set if the necessary information is known
        const category = new ExerciseCategory();
        category.category = 'testcategory';
        const categories = [category];
        exercise.categories = categories;
        exam.endDate = dayjs().subtract(1, 'day');
        component.exam = exam;

        component.ngOnInit();

        expect(component.isExamMode).toBe(true);
        expect(component.exerciseCategories).toEqual(categories);
        expect(component.exerciseStatusBadge).toBe('bg-danger');
    });

    it('should set the icon according to the exercise due date', () => {
        const dueDate1 = dayjs().subtract(2, 'days');
        exercise.dueDate = dueDate1;
        component.ngOnInit();
        expect(component.dueDate).toEqual(dueDate1);
        expect(component.exerciseStatusBadge).toBe('bg-danger');

        const dueDate2 = dayjs().add(1, 'day');
        participation.individualDueDate = dueDate2;
        component.studentParticipation = participation;
        component.ngOnInit();
        expect(component.dueDate).toEqual(dueDate2);
        expect(component.exerciseStatusBadge).toBe('bg-success');
    });

    it('should set the icon according to the exam end date', () => {
        exam.endDate = dayjs().subtract(1, 'day');
        component.exam = exam;
        component.ngOnInit();
        expect(component.exerciseStatusBadge).toBe('bg-danger');

        exam.endDate = dayjs().add(1, 'day');
        component.exam = exam;
        component.ngOnInit();
        expect(component.exerciseStatusBadge).toBe('bg-success');
    });

    it('should not set a due date in exam mode as no individual due dates exist', () => {
        exercise.dueDate = dayjs();
        exam.endDate = dayjs().add(1, 'day');
        component.exam = exam;

        component.ngOnInit();

        expect(component.dueDate).toBe(undefined);
    });
});
