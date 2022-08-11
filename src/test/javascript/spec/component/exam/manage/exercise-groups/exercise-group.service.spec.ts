import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { MockRouter } from '../../../../helpers/mocks/mock-router';

describe('Exercise Group Service', () => {
    let httpClient: any;
    let httpClientDeleteSpy: any;
    let service: ExerciseGroupService;

    beforeEach(() => {
        httpClient = {
            delete: jest.fn(),
        };
        httpClientDeleteSpy = jest.spyOn(httpClient, 'delete');
        service = new ExerciseGroupService(new MockRouter() as any as Router, httpClient as HttpClient);
    });

    it('should set additional parameters correctly in delete', () => {
        service.delete(1, 2, 3, true, false);
        expect(httpClientDeleteSpy).toHaveBeenCalledOnce();
        expect(httpClientDeleteSpy.mock.calls[0]).toHaveLength(2);
        expect(httpClientDeleteSpy.mock.calls[0][1].params.updates).toContainAllValues([
            { op: 's', param: 'deleteStudentReposBuildPlans', value: 'true' },
            { op: 's', param: 'deleteBaseReposBuildPlans', value: 'false' },
        ]);
    });
});
