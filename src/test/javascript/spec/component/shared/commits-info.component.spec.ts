import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CommitsInfoComponent } from 'app/exercises/programming/shared/commits-info/commits-info.component';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { CommitInfo } from 'app/entities/programming-submission.model';

describe('CommitsInfoComponent', () => {
    let component: CommitsInfoComponent;
    let fixture: ComponentFixture<CommitsInfoComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let programmingExerciseParticipationServiceSpy: jest.SpyInstance;
    const commitInfo1 = { hash: '123', author: 'author', timestamp: dayjs('2021-01-02'), message: 'commit message' } as CommitInfo;
    const commitInfo2 = { hash: '456', author: 'author2', timestamp: dayjs('2021-01-01'), message: 'other message' } as CommitInfo;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CommitsInfoComponent, MockPipe(ArtemisTranslatePipe)],
        });
        fixture = TestBed.createComponent(CommitsInfoComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        programmingExerciseParticipationServiceSpy = jest
            .spyOn(programmingExerciseParticipationService, 'retrieveCommitsInfoForParticipation')
            .mockReturnValue(of([commitInfo1, commitInfo2] as CommitInfo[]));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call participation service to retrieve commits onInit if no commits are passed as input and sort the commits ascending by timestamp', () => {
        component.participationId = 1;
        component.ngOnInit();
        expect(programmingExerciseParticipationServiceSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(component.commits).toEqual([commitInfo2, commitInfo1]);
    });

    it('should do nothing onInit if commits are passed as input', () => {
        component.commits = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];
        component.ngOnInit();
        expect(programmingExerciseParticipationServiceSpy).not.toHaveBeenCalled();
    });
});
