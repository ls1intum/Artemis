import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EMPTY, of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { InformationBox } from 'app/shared-ui/information-box/information-box.component';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { CourseExerciseGroupDetailComponent } from 'app/course/overview/course-exercises/group-detail/course-exercise-group-detail.component';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { EntityTitleService } from 'app/core/navbar/entity-title.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';

describe('CourseExerciseGroupDetailComponent', () => {
    let fixture: ComponentFixture<CourseExerciseGroupDetailComponent>;
    /** Server-computed achieved points per variant group id, as the ScoresStorageService would hold after a dashboard load. */
    let storedGroupPoints: Map<number, number>;

    const GROUP_ID = 10;

    /** Two exercises in group 10 (cap 15), each worth 10 points and fully included in the score, participations 101 and 102. */
    function exercisesInGroup(groupMaxPoints: number | undefined): Exercise[] {
        const reference = { id: GROUP_ID, title: 'Sorting variants', maxPoints: groupMaxPoints };
        return [
            {
                id: 1,
                type: ExerciseType.TEXT,
                maxPoints: 10,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                exerciseVariantGroup: reference,
                studentParticipations: [{ id: 101 }],
            } as unknown as Exercise,
            {
                id: 2,
                type: ExerciseType.TEXT,
                maxPoints: 10,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                exerciseVariantGroup: reference,
                studentParticipations: [{ id: 102 }],
            } as unknown as Exercise,
        ];
    }

    /** An additional variant of the given inclusion type worth 10 points, joining group 10. */
    function extraVariant(includedInOverallScore: IncludedInOverallScore, groupMaxPoints: number | undefined): Exercise {
        const reference = { id: GROUP_ID, title: 'Sorting variants', maxPoints: groupMaxPoints };
        return { id: 3, type: ExerciseType.TEXT, maxPoints: 10, includedInOverallScore, exerciseVariantGroup: reference } as unknown as Exercise;
    }

    async function setup(exercises: Exercise[]): Promise<void> {
        const course = { id: 1, exercises } as Course;
        const route = {
            params: of({ groupId: String(GROUP_ID) }),
            parent: { parent: { snapshot: { params: { courseId: '1' } } } },
        } as unknown as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [CourseExerciseGroupDetailComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(CourseOverviewExercisesService, { loadIfNeeded: () => of({ exercises } as CourseExercisesForOverviewDTO) }),
                MockProvider(CourseStorageService, { getCourse: () => course, subscribeToCourseUpdates: () => EMPTY }),
                MockProvider(EntityTitleService),
                MockProvider(ArtemisServerDateService, { now: () => dayjs() }),
                { provide: ScoresStorageService, useValue: { getStoredAchievedGroupPoints: (_courseId: number, groupId: number) => storedGroupPoints.get(groupId) } },
                { provide: ParticipationService, useClass: MockParticipationService },
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
        effectiveGroupMaxPoints: () => number;
        capReducesMaxPoints: () => boolean;
        variantsInfoBoxData: () => InformationBox;
        pointsInfoBoxData: () => InformationBox;
        groupDateInfoBoxes: () => InformationBox[];
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
        storedGroupPoints = new Map();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('surfaces the authoritative server-computed points for the group', async () => {
        // The server already caps and plagiarism-adjusts this value; the component must display it verbatim.
        storedGroupPoints.set(GROUP_ID, 13);
        await setup(exercisesInGroup(15));
        expect(achievedGroupPoints()).toBe(13);
    });

    it('falls back to 0 when the server stored no contribution for the group', async () => {
        // e.g. before the dashboard scores load, or when the group contributes nothing.
        await setup(exercisesInGroup(15));
        expect(achievedGroupPoints()).toBe(0);
    });

    describe('effectiveGroupMaxPoints', () => {
        it('shows the variants sum when no cap is configured', async () => {
            await setup(exercisesInGroup(undefined));
            expect(comp().effectiveGroupMaxPoints()).toBe(20);
            expect(comp().capReducesMaxPoints()).toBe(false);
        });

        it('caps the maximum at a binding cap (below the variants sum)', async () => {
            await setup(exercisesInGroup(15));
            expect(comp().effectiveGroupMaxPoints()).toBe(15);
            expect(comp().capReducesMaxPoints()).toBe(true);
        });

        it('ignores a cap that exceeds the variants sum and shows the sum instead', async () => {
            await setup(exercisesInGroup(100));
            // A student can never earn more than the 20 the variants offer, so the course maximum is 20, not 100.
            expect(comp().effectiveGroupMaxPoints()).toBe(20);
            expect(comp().capReducesMaxPoints()).toBe(false);
        });

        it('treats a zero cap as a binding cap that discards all group points', async () => {
            await setup(exercisesInGroup(0));
            expect(comp().effectiveGroupMaxPoints()).toBe(0);
            expect(comp().capReducesMaxPoints()).toBe(true);
        });

        it('excludes a bonus variant from the maximum, matching the server inclusion rule', async () => {
            // Two INCLUDED_COMPLETELY variants (10 each) + one INCLUDED_AS_BONUS variant: the course maximum only counts
            // the first two, so the cap of 25 is not binding even though the raw sum of all variants would be 30.
            await setup([...exercisesInGroup(25), extraVariant(IncludedInOverallScore.INCLUDED_AS_BONUS, 25)]);
            expect(comp().exerciseSumMaxPoints()).toBe(20);
            expect(comp().effectiveGroupMaxPoints()).toBe(20);
            expect(comp().capReducesMaxPoints()).toBe(false);
        });

        it('excludes a not-included variant from the maximum, matching the server inclusion rule', async () => {
            await setup([...exercisesInGroup(15), extraVariant(IncludedInOverallScore.NOT_INCLUDED, 15)]);
            expect(comp().exerciseSumMaxPoints()).toBe(20);
            // The cap of 15 binds against the included sum of 20, not the raw sum of 30.
            expect(comp().effectiveGroupMaxPoints()).toBe(15);
            expect(comp().capReducesMaxPoints()).toBe(true);
        });
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

    describe('helpers', () => {
        it('returns the graded participation and the exercise link', async () => {
            await setup(exercisesInGroup(undefined));
            const exercise = comp().exercises()[0];
            expect(comp().exerciseParticipation(exercise)?.id).toBe(101);
            expect(comp().exerciseParticipation({} as Exercise)).toBeUndefined();
            expect(comp().exerciseLink(exercise)).toBe('/courses/1/exercises/1');
        });

        it('selects the graded participation even when a practice run is listed first', async () => {
            // The dashboard response includes practice test runs in unspecified order; the card must not show them.
            const exercises = exercisesInGroup(undefined);
            exercises[0].studentParticipations = [{ id: 201, testRun: true } as StudentParticipation, { id: 101 } as StudentParticipation];
            await setup(exercises);
            expect(comp().exerciseParticipation(comp().exercises()[0])?.id).toBe(101);
        });
    });
});
