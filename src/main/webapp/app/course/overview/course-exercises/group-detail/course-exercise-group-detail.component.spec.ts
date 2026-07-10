import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { InformationBox } from 'app/shared-ui/information-box/information-box.component';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { CourseExerciseGroupDetailComponent } from 'app/course/overview/course-exercises/group-detail/course-exercise-group-detail.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EntityTitleService } from 'app/core/navbar/entity-title.service';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationResultDTO } from 'app/course/shared/entities/course-for-dashboard-dto';

describe('CourseExerciseGroupDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseExerciseGroupDetailComponent>;
    let storedResults: Map<number, ParticipationResultDTO>;

    const GROUP_ID = 10;

    /** Two exercises in group 10 (cap 15), each worth 10 points, participations 101 and 102. */
    function exercisesInGroup(groupMaxPoints: number | undefined): Exercise[] {
        const reference = { id: GROUP_ID, title: 'Sorting variants', maxPoints: groupMaxPoints };
        return [
            { id: 1, type: ExerciseType.TEXT, maxPoints: 10, exerciseVariantGroup: reference, studentParticipations: [{ id: 101 }], problemStatement: 'a' } as unknown as Exercise,
            { id: 2, type: ExerciseType.TEXT, maxPoints: 10, exerciseVariantGroup: reference, studentParticipations: [{ id: 102 }], problemStatement: 'b' } as unknown as Exercise,
        ];
    }

    async function setup(exercises: Exercise[], options?: { getExerciseDetails?: () => Observable<HttpResponse<{ exercise: Exercise }>> }): Promise<void> {
        const course = { id: 1, exercises } as Course;
        const route = {
            params: of({ groupId: String(GROUP_ID) }),
            parent: { parent: { snapshot: { params: { courseId: '1' } } } },
        } as unknown as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [CourseExerciseGroupDetailComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(CourseManagementService, { findOneForDashboard: () => of(new HttpResponse({ body: course })) }),
                MockProvider(ExerciseService, { getExerciseDetails: (options?.getExerciseDetails ?? (() => EMPTY)) as never }),
                MockProvider(EntityTitleService),
                MockProvider(ProgrammingExercisePlantUmlExtensionWrapper, {
                    subscribeForInjectableElementsFound: () => EMPTY,
                    // A markdown-it plugin is a function; a bare object would make markdownIt.use() throw.
                    getExtension: () => (() => {}) as never,
                }),
                MockProvider(ArtemisServerDateService, { now: () => dayjs() }),
                MockProvider(DomSanitizer, { bypassSecurityTrustHtml: (value: string) => value }),
                { provide: ScoresStorageService, useValue: { getStoredParticipationResult: (id: number) => storedResults.get(id) } },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            // Render nothing: this spec exercises the component's scoring logic, not its template.
            .overrideComponent(CourseExerciseGroupDetailComponent, { set: { template: '' } })
            .compileComponents();

        fixture = TestBed.createComponent(CourseExerciseGroupDetailComponent);
    }

    /** Access to protected members under test. */
    function comp(): {
        group: () => { id?: number; title?: string } | undefined;
        exercises: () => Exercise[];
        exerciseSumMaxPoints: () => number;
        variantsInfoBoxData: () => InformationBox;
        pointsInfoBoxData: () => InformationBox;
        groupDateInfoBoxes: () => InformationBox[];
        renderedStatements: () => Map<number, SafeHtml>;
        exerciseParticipation: (exercise: Exercise) => StudentParticipation | undefined;
        exerciseLink: (exercise: Exercise) => string;
    } {
        return fixture.componentInstance as never;
    }

    /** Reads the protected computed under test. */
    function achievedGroupPoints(): number {
        return (fixture.componentInstance as unknown as { achievedGroupPoints: () => number }).achievedGroupPoints();
    }

    beforeEach(() => {
        storedResults = new Map();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('sums the server-provided results of the group members', async () => {
        storedResults.set(101, { participationId: 101, rated: true, score: 80 });
        storedResults.set(102, { participationId: 102, rated: true, score: 100 });
        await setup(exercisesInGroup(undefined));
        // 80% of 10 + 100% of 10 = 8 + 10 = 18 (no cap configured).
        expect(achievedGroupPoints()).toBe(18);
    });

    it('caps the summed points at the group maxPoints', async () => {
        storedResults.set(101, { participationId: 101, rated: true, score: 80 });
        storedResults.set(102, { participationId: 102, rated: true, score: 100 });
        await setup(exercisesInGroup(15));
        // 18 achieved, capped at the group's 15.
        expect(achievedGroupPoints()).toBe(15);
    });

    it('ignores unrated results', async () => {
        storedResults.set(101, { participationId: 101, rated: true, score: 80 });
        storedResults.set(102, { participationId: 102, rated: false, score: 100 });
        await setup(exercisesInGroup(15));
        // Only the rated result of exercise 1 counts: 8.
        expect(achievedGroupPoints()).toBe(8);
    });

    it('ignores participations without a stored (server-computed) result', async () => {
        storedResults.set(101, { participationId: 101, rated: true, score: 80 });
        // Participation 102 has no stored result: it must not fall back to any local result.
        await setup(exercisesInGroup(15));
        expect(achievedGroupPoints()).toBe(8);
    });

    describe('group resolution', () => {
        it('resolves the group, its members and the sum of max points from the dashboard exercises', async () => {
            await setup(exercisesInGroup(15));
            expect(comp().group()?.id).toBe(GROUP_ID);
            expect(
                comp()
                    .exercises()
                    .map((e) => e.id),
            ).toEqual([1, 2]);
            expect(comp().exerciseSumMaxPoints()).toBe(20);
            expect(comp().variantsInfoBoxData().content.value).toBe(2);
            expect(comp().pointsInfoBoxData().isContentComponent).toBe(true);
        });

        it('resolves no group when the course has no exercises of that group', async () => {
            await setup([{ id: 5, type: ExerciseType.TEXT } as Exercise]);
            expect(comp().group()).toBeUndefined();
            expect(comp().exercises()).toEqual([]);
        });

        it('publishes the group title to the entity title service', async () => {
            await setup(exercisesInGroup(15));
            const titleService = TestBed.inject(EntityTitleService);
            const setTitleSpy = vi.spyOn(titleService, 'setTitle');
            fixture.detectChanges();
            await fixture.whenStable();
            expect(setTitleSpy).toHaveBeenCalledWith(expect.anything(), [GROUP_ID], 'Sorting variants');
        });
    });

    describe('groupDateInfoBoxes', () => {
        function exercisesWithGroupDates(dates: { dueDate?: dayjs.Dayjs; startDate?: dayjs.Dayjs; assessmentDueDate?: dayjs.Dayjs }): Exercise[] {
            const reference = { id: GROUP_ID, title: 'Sorting variants', ...dates };
            return [{ id: 1, type: ExerciseType.TEXT, exerciseVariantGroup: reference, problemStatement: 'a' } as unknown as Exercise];
        }

        it('shows the due-over box for a past due date', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().subtract(2, 'day') }));
            const boxes = comp().groupDateInfoBoxes();
            expect(boxes.map((b) => b.title)).toEqual(['artemisApp.courseOverview.exerciseDetails.submissionDueOver']);
        });

        it('shows a relative due date with tooltip when due within a week', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().add(3, 'day') }));
            const box = comp().groupDateInfoBoxes()[0];
            expect(box.title).toBe('artemisApp.courseOverview.exerciseDetails.submissionDue');
            expect(box.content.type).toBe('timeAgo');
            expect(box.tooltip).toBeDefined();
        });

        it('shows an absolute due date when due later than a week', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().add(2, 'week') }));
            const box = comp().groupDateInfoBoxes()[0];
            expect(box.content.type).toBe('dateTime');
            expect(box.tooltip).toBeUndefined();
        });

        it('marks a due date within 24 hours as danger', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().add(2, 'hour') }));
            expect(comp().groupDateInfoBoxes()[0].contentColor).toBe('danger');
        });

        it('adds the assessment-due box when the due date passed and assessment is still open', async () => {
            await setup(exercisesWithGroupDates({ dueDate: dayjs().subtract(1, 'day'), assessmentDueDate: dayjs().add(1, 'day') }));
            const titles = comp()
                .groupDateInfoBoxes()
                .map((b) => b.title);
            expect(titles).toContain('artemisApp.courseOverview.exerciseDetails.assessmentDue');
        });

        it('adds the start-date box for a future start date', async () => {
            await setup(exercisesWithGroupDates({ startDate: dayjs().add(2, 'day') }));
            const titles = comp()
                .groupDateInfoBoxes()
                .map((b) => b.title);
            expect(titles).toEqual(['artemisApp.courseOverview.exerciseDetails.startDate']);
        });

        it('shows no boxes without group dates', async () => {
            await setup(exercisesWithGroupDates({}));
            expect(comp().groupDateInfoBoxes()).toEqual([]);
        });
    });

    describe('problem statements', () => {
        it('renders inline problem statements of the members', async () => {
            await setup(exercisesInGroup(undefined));
            fixture.detectChanges();
            await fixture.whenStable();
            const rendered = comp().renderedStatements();
            expect(rendered.get(1)).toBeDefined();
            expect(rendered.get(2)).toBeDefined();
        });

        it('batch-loads missing problem statements from the exercise details endpoint', async () => {
            const reference = { id: GROUP_ID, title: 'Sorting variants' };
            const member = { id: 1, type: ExerciseType.TEXT, exerciseVariantGroup: reference } as unknown as Exercise;
            const details = of(new HttpResponse({ body: { exercise: { id: 1, type: ExerciseType.TEXT, problemStatement: 'loaded **statement**' } as Exercise } }));
            await setup([member], { getExerciseDetails: () => details });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(comp().renderedStatements().get(1)).toBeDefined();
        });

        it('releases requested ids on a failed batch so a retry is possible', async () => {
            const reference = { id: GROUP_ID, title: 'Sorting variants' };
            const member = { id: 1, type: ExerciseType.TEXT, exerciseVariantGroup: reference } as unknown as Exercise;
            const detailsSpy = vi.fn(() => throwError(() => new Error('boom')));
            await setup([member], { getExerciseDetails: detailsSpy });
            fixture.detectChanges();
            await fixture.whenStable();

            expect(detailsSpy).toHaveBeenCalled();
            const requested = (fixture.componentInstance as unknown as { problemStatementsRequested: Set<number> })['problemStatementsRequested'];
            expect(requested.has(1)).toBe(false);
        });
    });

    describe('helpers', () => {
        it('returns the first participation and the exercise link', async () => {
            await setup(exercisesInGroup(undefined));
            const exercise = comp().exercises()[0];
            expect(comp().exerciseParticipation(exercise)?.id).toBe(101);
            expect(comp().exerciseParticipation({} as Exercise)).toBeUndefined();
            expect(comp().exerciseLink(exercise)).toBe('/courses/1/exercises/1');
        });
    });
});
