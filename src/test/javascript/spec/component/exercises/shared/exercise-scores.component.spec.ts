import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ArtemisTestModule } from '../../../test.module';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateService, TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { ExerciseScoresComponent } from 'app/exercises/shared/exercise-scores/exercise-scores.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { of, Subscription } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { MockTranslateValuesDirective } from '../../course/course-scores/course-scores.component.spec';
import { DifferencePipe } from 'ngx-moment';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { Team } from 'app/entities/team.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('Exercise Scores Component', () => {
    let component: ExerciseScoresComponent;
    let fixture: ComponentFixture<ExerciseScoresComponent>;
    let resultService: ResultService;
    let programmingSubmissionService: ProgrammingSubmissionService;

    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.TEXT,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };

    const course = new Course();
    course.id = 1;

    const team: Team = {
        name: 'name',
        shortName: 'shortName',
        students: [
            {
                login: 'login1',
                name: 'name1',
                guidedTourSettings: [],
            },
            {
                login: 'login2',
                name: 'name2',
                guidedTourSettings: [],
            },
        ],
    };

    const result = new Result();
    const participation: ProgrammingExerciseStudentParticipation = {
        buildPlanId: '1',
        userIndependentRepositoryUrl: 'url',
        participantIdentifier: 'participationId',
        participantName: 'participantName',
    };
    result.participation = participation;
    result.assessmentType = AssessmentType.MANUAL;

    const router = new MockRouter();

    const route = { data: of({ courseId: 1 }), children: [] } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), TranslateTestingModule, FormsModule, NgxDatatableModule],
            declarations: [
                ExerciseScoresComponent,
                MockComponent(AlertComponent),
                MockComponent(ExerciseScoresExportButtonComponent),
                MockComponent(ProgrammingAssessmentRepoExportButtonComponent),
                MockComponent(SubmissionExportButtonComponent),
                MockComponent(DataTableComponent),
                MockComponent(ResultComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(FeatureToggleLinkDirective),
                MockDirective(MockTranslateValuesDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: Router, useValue: router },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                DifferencePipe,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseScoresComponent);
                component = fixture.componentInstance;
                resultService = TestBed.inject(ResultService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
                component.exercise = exercise;
                sinon.stub(programmingSubmissionService, 'unsubscribeAllWebsocketTopics');
                component.paramSub = new Subscription();
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should get exercise participation link for exercise without an exercise group', () => {
        const expectedLink = ['/course-management', course.id!.toString(), 'text-exercises', exercise.id!.toString(), 'participations', '1', 'submissions'];
        component.course = course;
        component.exercise = exercise;

        const returnedLink = component.getExerciseParticipationsLink(1);

        expect(returnedLink).to.have.same.members(expectedLink);
    });

    it('should get exercise participation link for exercise with an exercise group', () => {
        const expectedLink = ['/course-management', course.id!.toString(), 'exams', '1', 'exercise-groups', '1', 'text-exercises', exercise.id!.toString(), 'participations', '2'];
        component.course = course;
        component.exercise = exercise;
        component.exercise.exerciseGroup = {
            id: 1,
            exam: {
                id: 1,
            },
        };

        const returnedLink = component.getExerciseParticipationsLink(2);

        expect(returnedLink).to.have.same.members(expectedLink);
    });

    it('should update result', fakeAsync(() => {
        component.updateResultFilter(component.FilterProp.SUCCESSFUL);

        expect(component.isLoading).to.equal(true);
        tick();
        expect(component.resultCriteria.filterProp).to.equal(component.FilterProp.SUCCESSFUL);
        expect(component.isLoading).to.equal(false);
    }));

    it('should filter result prop "successful"', () => {
        component.resultCriteria.filterProp = component.FilterProp.SUCCESSFUL;
        result.successful = true;

        expect(component.filterResultByProp(result)).to.be.true;
    });

    it('should filter result prop "unsuccessful"', () => {
        component.resultCriteria.filterProp = component.FilterProp.UNSUCCESSFUL;
        result.successful = true;

        expect(component.filterResultByProp(result)).to.be.false;
    });

    it('should filter result prop "build failed"', () => {
        component.resultCriteria.filterProp = component.FilterProp.BUILD_FAILED;

        expect(component.filterResultByProp(result)).to.be.false;
    });

    it('should filter result prop "manual"', () => {
        component.resultCriteria.filterProp = component.FilterProp.MANUAL;

        expect(component.filterResultByProp(result)).to.be.true;
    });

    it('should filter result prop "automatic"', () => {
        component.resultCriteria.filterProp = component.FilterProp.AUTOMATIC;

        expect(component.filterResultByProp(result)).to.be.false;
    });

    it('should filter result prop default value', () => {
        expect(component.filterResultByProp(result)).to.be.true;
    });

    it('should handle result size change', () => {
        component.handleResultsSizeChange(10);

        expect(component.filteredResultsSize).to.equal(10);
    });

    it('should return build plan id', () => {
        expect(component.buildPlanId(result)).to.equal('1');
    });

    it('should return project key', () => {
        const programmingExercise: ProgrammingExercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            projectKey: 'key',
        };
        component.exercise = programmingExercise;

        expect(component.projectKey()).to.equal('key');
    });

    it('should return repository link', () => {
        expect(component.getRepositoryLink(result)).to.equal('url');
    });

    it('should export names correctly for student participation', () => {
        component.results = [result];
        const rows = ['data:text/csv;charset=utf-8,participantName'];
        const resultServiceStub = sinon.stub(resultService, 'triggerDownloadCSV');

        component.exportNames();

        expect(resultServiceStub).to.have.been.calledOnceWith(rows, 'results-names.csv');
    });

    it('should export names correctly for team participation', () => {
        participation.team = team;
        component.results = [result];
        const rows = ['data:text/csv;charset=utf-8,Team Name,Team Short Name,Students', 'name,shortName,"name1, name2"'];
        const resultServiceStub = sinon.stub(resultService, 'triggerDownloadCSV');

        component.exportNames();

        expect(resultServiceStub).to.have.been.calledOnceWith(rows, 'results-names.csv');
        participation.team = undefined;
    });

    it('should search result for student participation', () => {
        participation.student = {
            login: 'login',
            name: 'name',
            guidedTourSettings: [],
        };

        expect(component.searchResultFormatter(result)).to.equal('login (name)');

        participation.student = undefined;
    });

    it('should search result for team', () => {
        participation.team = team;
        const expectedResult = 'name (shortName) ⟹ name1 (login1), name2 (login2)';

        expect(component.searchResultFormatter(result)).to.equal(expectedResult);

        participation.team = undefined;
    });

    it('should search result and return empty string', () => {
        expect(component.searchResultFormatter(result)).to.equal('');
    });

    it('should search text from result', () => {
        expect(component.searchTextFromResult(result)).to.equal('participationId');
    });

    it('should refresh properly', () => {
        const resultServiceStub = sinon.stub(resultService, 'getResults').returns(of(new HttpResponse<Result[]>({ body: [result] })));

        component.refresh();

        expect(resultServiceStub).to.have.been.calledOnceWith(component.exercise);
        expect(component.results).to.have.same.members([result]);
        expect(component.isLoading).to.be.false;
    });

    it('should format date correctly', () => {
        const date = new Date(2021, 4, 8, 21, 47, 17);

        expect(component.formatDate(date)).to.equal('2021-05-08 21:47:17');

        expect(component.formatDate(undefined)).to.equal('');
    });
});
