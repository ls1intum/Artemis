import { TestBed } from '@angular/core/testing';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { HttpClient, provideHttpClient } from '@angular/common/http';

describe('Exercise Group Service', () => {
    let httpClient: any;
    let httpClientDeleteSpy: any;
    let service: ExerciseGroupService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient()],
        });

        service = TestBed.inject(ExerciseGroupService);
        httpClient = TestBed.inject(HttpClient);
        httpClientDeleteSpy = jest.spyOn(httpClient, 'delete').mockImplementation(() => {
            return {};
        });
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
