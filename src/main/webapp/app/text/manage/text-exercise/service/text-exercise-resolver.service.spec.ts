/**
 * Tests for TextExerciseResolver.
 * Verifies the resolver's behavior for resolving text exercises in different contexts.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { MockProvider } from 'ng-mocks';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ActivatedRouteSnapshot } from '@angular/router';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { of } from 'rxjs';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Course } from 'app/course/shared/entities/course.model';

describe('TextExerciseResolver', () => {
    let resolver: TextExerciseResolver;
    let route: ActivatedRouteSnapshot;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(TextExerciseService),
                MockProvider(ExerciseGroupService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        resolver = TestBed.inject(TextExerciseResolver);
        route = new ActivatedRouteSnapshot();
        vi.spyOn(route, 'pathFromRoot', 'get').mockReturnValue([route]);
    });

    it('should resolve a TextExercise when exerciseId is provided', () => {
        const dummyExercise = new TextExercise(undefined, undefined);
        const textExerciseService = TestBed.inject(TextExerciseService);
        vi.spyOn(textExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyExercise })));

        route.params = { exerciseId: '123' };

        resolver.resolve(route).subscribe((result) => {
            expect(result).toEqual(dummyExercise);
        });

        expect(textExerciseService.find).toHaveBeenCalledWith('123', true);
    });

    it('should resolve a TextExercise for a course and exercise group', () => {
        const dummyExerciseGroup = { id: 3 } as ExerciseGroup;
        const exerciseGroupService = TestBed.inject(ExerciseGroupService);
        vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyExerciseGroup })));

        route.params = { courseId: '1', examId: '2', exerciseGroupId: '3' };

        resolver.resolve(route).subscribe((result) => {
            expect(result).toEqual(
                expect.objectContaining({
                    exerciseGroup: dummyExerciseGroup,
                }),
            );
        });

        expect(exerciseGroupService.find).toHaveBeenCalledWith('1', '2', '3');
    });

    it('should resolve a TextExercise for a course without an exercise group', () => {
        const dummyCourse = { id: 1 } as Course;
        const courseService = TestBed.inject(CourseManagementService);
        vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyCourse })));

        route.params = { courseId: '1' };

        resolver.resolve(route).subscribe((result) => {
            expect(result).toEqual(
                expect.objectContaining({
                    course: dummyCourse,
                }),
            );
        });

        expect(courseService.find).toHaveBeenCalledWith('1');
    });
    it('should resolve exam details from an ancestor with the same options', () => {
        const detailService = TestBed.inject(TextExerciseService);
        const exercise = new TextExercise(undefined, undefined);
        const examFind = vi.spyOn(detailService, 'findForExam').mockReturnValue(of(new HttpResponse({ body: exercise })));
        vi.spyOn(detailService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const snapshot = new ActivatedRouteSnapshot();
        snapshot.params = { exerciseId: 123 };
        const ancestor = new ActivatedRouteSnapshot();
        ancestor.params = { examId: 7 };
        vi.spyOn(snapshot, 'pathFromRoot', 'get').mockReturnValue([ancestor, snapshot]);
        resolver.resolve(snapshot).subscribe((result) => expect(result).toBe(exercise));
        expect(examFind).toHaveBeenCalledExactlyOnceWith(123, true);
        expect(detailService.find).not.toHaveBeenCalled();
    });

    it.each([
        ['true', false],
        ['false', true],
        [undefined, true],
    ])('uses import source flag %s before target exam context %s after reload', (sourceFlag, targetIsExam) => {
        const detailService = TestBed.inject(TextExerciseService);
        const exercise = new TextExercise(undefined, undefined);
        const courseFind = vi.spyOn(detailService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const examFind = vi.spyOn(detailService, 'findForExam').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const snapshot = new ActivatedRouteSnapshot();
        snapshot.params = { exerciseId: 123, ...(targetIsExam ? { examId: 7 } : {}) };
        snapshot.queryParams = sourceFlag === undefined ? {} : { sourceIsExamExercise: sourceFlag };
        vi.spyOn(snapshot, 'routeConfig', 'get').mockReturnValue({ path: 'exercises/:exerciseId/import' });
        vi.spyOn(snapshot, 'pathFromRoot', 'get').mockReturnValue([snapshot]);
        resolver.resolve(snapshot).subscribe((result) => expect(result).toBe(exercise));
        expect(sourceFlag === 'true' ? examFind : courseFind).toHaveBeenCalledExactlyOnceWith(123, true);
        expect(sourceFlag === 'true' ? courseFind : examFind).not.toHaveBeenCalled();
    });
});
