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
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';
import { LockRepositoryPolicy } from 'app/entities/submission-policy.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

describe('HeaderExercisePageWithDetails', () => {
    let component: HeaderExercisePageWithDetailsComponent;

    let exam: Exam;
    let exercise: ProgrammingExercise;
    let participation: StudentParticipation;

    const submissionCountingOne: ProgrammingSubmission = { type: SubmissionType.MANUAL, results: [new Result()], commitHash: 'qwer' };
    const submissionCountingTwo: ProgrammingSubmission = { type: SubmissionType.MANUAL, results: [new Result()], commitHash: 'asdf' };
    const submissionNotManual: ProgrammingSubmission = { type: SubmissionType.INSTRUCTOR };
    const submissionNoResult: ProgrammingSubmission = { type: SubmissionType.MANUAL, commitHash: 'yxcv' };

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
                exercise.dueDate = undefined;
                exercise.assessmentType = AssessmentType.AUTOMATIC;
                component.exercise = exercise;

                exam = new Exam();
                participation = new StudentParticipation(ParticipationType.PROGRAMMING);
            });
    });

    it('should initialise badges, icons, and categories', () => {
        component.ngOnInit();

        expect(component.exerciseCategories).toEqual([]);
        expect(component.nextDueDateIndex).toBeUndefined();
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-danger', 'bg-danger', 'bg-danger']);
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

        expect(component.exerciseCategories).toEqual(categories);
        expect(component.nextDueDateIndex).toBeUndefined();
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-danger']);
    });

    it('should set the icon according to the exercise due date', () => {
        exercise.dueDate = dayjs().subtract(2, 'days');
        exercise.assessmentType = AssessmentType.MANUAL;

        exercise.releaseDate = dayjs().add(1, 'day');
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBe(0);
        expect(component.statusBadges).toStrictEqual(['bg-success', 'bg-success', 'bg-success', 'bg-success']);

        exercise.releaseDate = undefined;
        exercise.startDate = dayjs().add(1, 'day');
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBe(0);
        expect(component.statusBadges).toStrictEqual(['bg-success', 'bg-success', 'bg-success', 'bg-success']);

        exercise.startDate = undefined;
        const dueDate1 = dayjs().add(1, 'day');
        participation.individualDueDate = dueDate1;
        component.studentParticipation = participation;
        component.ngOnInit();
        expect(component.dueDate).toEqual(dueDate1);
        expect(component.nextDueDateIndex).toBe(1);
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-success', 'bg-success', 'bg-success']);

        participation.individualDueDate = undefined;
        exercise.assessmentDueDate = dayjs().add(2, 'days');
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBe(2);
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-danger', 'bg-success', 'bg-success']);

        exercise.assessmentDueDate = dayjs().subtract(1, 'days');
        component.course = { maxComplaintTimeDays: 7 } as Course;
        participation.results = [{ rated: true, completionDate: dayjs() } as Result];
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBe(3);
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-danger', 'bg-danger', 'bg-success']);

        participation.submissionCount = 1;
        participation.results = [{ rated: false } as Result];
        exercise.assessmentType = AssessmentType.MANUAL;
        exercise.dueDate = dayjs().subtract(3, 'months');
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBe(4);
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-danger', 'bg-danger', 'bg-danger']);

        exercise.assessmentDueDate = dayjs().subtract(2, 'months');
        participation.results = [{ rated: true, completionDate: dayjs().subtract(1, 'month') } as Result];
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBeUndefined();
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-danger', 'bg-danger', 'bg-danger']);
    });

    it('should set the icon according to the exam end date', () => {
        exam.endDate = dayjs().subtract(1, 'day');
        component.exam = exam;
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBeUndefined();
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-danger']);

        exam.publishResultsDate = dayjs().add(12, 'hours');
        component.exam = exam;
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBe(1);
        expect(component.statusBadges).toStrictEqual(['bg-danger', 'bg-success']);

        exam.publishResultsDate = dayjs().subtract(12, 'hours');
        exam.endDate = dayjs().add(1, 'day');
        component.exam = exam;
        component.ngOnInit();
        expect(component.nextDueDateIndex).toBe(0);
        expect(component.statusBadges).toStrictEqual(['bg-success', 'bg-success']);
    });

    it('should not set a due date in exam mode as no individual due dates exist', () => {
        exercise.dueDate = dayjs();
        exam.endDate = dayjs().add(1, 'day');
        component.exam = exam;

        component.ngOnInit();

        expect(component.dueDate).toBeUndefined();
    });

    it('should count number of submissions correctly', () => {
        participation.submissions = [submissionCountingOne, submissionNoResult, submissionCountingTwo, submissionNotManual];
        component.studentParticipation = participation;
        component.submissionPolicy = new LockRepositoryPolicy();

        component.ngOnChanges();

        expect(component.numberOfSubmissions).toBe(2);
    });

    it('should count number of submissions correctly with compensation', () => {
        participation.submissions = [submissionNoResult, submissionCountingOne, submissionCountingTwo, submissionNotManual];
        component.studentParticipation = participation;
        component.submissionPolicy = new LockRepositoryPolicy();

        component.ngOnChanges();

        expect(component.numberOfSubmissions).toBe(3);
    });
});
