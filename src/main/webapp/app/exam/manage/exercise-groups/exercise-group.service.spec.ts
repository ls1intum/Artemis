import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { HttpClient, provideHttpClient } from '@angular/common/http';

describe('Exercise Group Service', () => {
    setupTestBed({ zoneless: true });

    let httpClient: any;
    let httpClientDeleteSpy: any;
    let service: ExerciseGroupService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient()],
        });

        service = TestBed.inject(ExerciseGroupService);
        httpClient = TestBed.inject(HttpClient);
        httpClientDeleteSpy = vi.spyOn(httpClient, 'delete').mockImplementation(() => {
            return {} as any;
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set additional parameters correctly in delete', () => {
        service.delete(1, 2, 3, true, false);
        expect(httpClientDeleteSpy).toHaveBeenCalledOnce();
        expect(httpClientDeleteSpy.mock.calls[0]).toHaveLength(2);
        const updates = httpClientDeleteSpy.mock.calls[0][1].params.updates;
        expect(updates).toEqual(
            expect.arrayContaining([
                { op: 's', param: 'deleteStudentReposBuildPlans', value: 'true' },
                { op: 's', param: 'deleteBaseReposBuildPlans', value: 'false' },
            ]),
        );
    });
});
