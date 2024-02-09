import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ActivatedRoute } from '@angular/router';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { CommitHistoryComponent } from 'app/localvc/commit-history/commit-history.component';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import dayjs from 'dayjs';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { of } from 'rxjs';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { CommitsInfoComponent } from 'app/exercises/programming/shared/commits-info/commits-info.component';
import { MockComponent } from 'ng-mocks';
describe('CommitHistoryComponent', () => {
    let component: CommitHistoryComponent;
    let fixture: ComponentFixture<CommitHistoryComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let activatedRoute: MockActivatedRoute;

    // Define mock data for participation and commits
    const mockParticipation: ProgrammingExerciseStudentParticipation = {
        id: 2,
        repositoryUri: 'student-repo-uri',
        exercise: { id: 1, numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()], studentAssignedTeamIdComputed: true, secondCorrectionEnabled: true },
        results: [
            { id: 1, successful: true, completionDate: dayjs.Dayjs('2021-01-02'), submission: { id: 2, commitHash: 'commit2' } },
            { id: 2, successful: false, completionDate: dayjs.Dayjs('2021-01-03'), submission: { id: 3, commitHash: 'commit3' } },
        ],
        submissions: [
            { id: 2, commitHash: 'commit2' },
            { id: 3, commitHash: 'commit3' },
        ] as ProgrammingSubmission[],
    };
    const commit1: CommitInfo = { hash: 'commit1', author: 'author1', message: 'message1', timestamp: dayjs.Dayjs('2021-01-01') };
    const commit2: CommitInfo = { hash: 'commit2', author: 'author2', message: 'message2', timestamp: dayjs.Dayjs('2021-01-02') };
    const commit3: CommitInfo = { hash: 'commit3', author: 'author3', message: 'message3', timestamp: dayjs.Dayjs('2021-01-03') };
    const mockCommits: CommitInfo[] = [commit2, commit3, commit1];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CommitHistoryComponent, MockComponent(CommitsInfoComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
            ],
        }).compileComponents();
    });

    function setupComponent() {
        fixture = TestBed.createComponent(CommitHistoryComponent);
        component = fixture.componentInstance;
        activatedRoute = fixture.debugElement.injector.get(ActivatedRoute) as MockActivatedRoute;
        programmingExerciseParticipationService = fixture.debugElement.injector.get(ProgrammingExerciseParticipationService);

        activatedRoute.setParameters({ participationId: 2 });
        jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockReturnValue(of(mockParticipation));
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForParticipation').mockReturnValue(of(mockCommits));

        fixture.detectChanges();
    }

    it('should create', () => {
        setupComponent();
        expect(component).toBeTruthy();
    });

    it('should load student participation', () => {
        setupComponent();

        // Expectations
        expect(component.studentParticipation).toEqual(mockParticipation);
        expect(component.studentParticipation.results).toHaveLength(2); // Updated to reflect the number of results
        expect(component.studentParticipation.results[0]!.participation).toEqual(mockParticipation);
        expect(component.studentParticipation.results[1]!.participation).toEqual(mockParticipation);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should load commits', () => {
        setupComponent();

        // Expectations
        expect(component.studentParticipation).toEqual(mockParticipation);
        expect(component.commits).toEqual([commit3, commit2, commit1]); // Updated to reflect the correct order

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
    });

    it('should set commit results', () => {
        setupComponent();

        // Expectations
        expect(component.studentParticipation).toEqual(mockParticipation);
        expect(component.commits).toEqual([commit3, commit2, commit1]); // Updated to reflect the correct order

        expect(component.commits[0].result).toEqual(mockParticipation.results![1]);
        expect(component.commits[1].result).toEqual(mockParticipation.results![0]);
        expect(component.commits[2].result).toBeUndefined();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
    });
});
