import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DomSanitizer } from '@angular/platform-browser';
import { EMPTY, of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
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
                MockProvider(CourseManagementService, { findOneForDashboard: () => of(new HttpResponse({ body: course })) }),
                MockProvider(ExerciseService, { getExerciseDetails: () => EMPTY }),
                MockProvider(EntityTitleService),
                MockProvider(ProgrammingExercisePlantUmlExtensionWrapper, { subscribeForInjectableElementsFound: () => EMPTY }),
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
});
