import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute, Params } from '@angular/router';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import * as moment from 'moment';
import { User } from 'app/core/user/user.model';
import { Team } from 'app/entities/team.model';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/exercises/programming/participate/programming-submission.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('ParticipationComponent', () => {
    let component: ParticipationComponent;
    let componentFixture: ComponentFixture<ParticipationComponent>;
    let participationService: ParticipationService;
    let exerciseService: ExerciseService;
    let submissionService: ProgrammingSubmissionService;

    const exercise: Exercise = { numberOfAssessmentsOfCorrectionRounds: [], studentAssignedTeamIdComputed: false, id: 1, secondCorrectionEnabled: true };

    const route = { params: of({ exerciseId: 1 } as Params) } as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            declarations: [ParticipationComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(TranslateService),
            ],
        })
            .overrideTemplate(ParticipationComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ParticipationComponent);
                component = componentFixture.componentInstance;
                participationService = TestBed.inject(ParticipationService);
                exerciseService = TestBed.inject(ExerciseService);
                submissionService = TestBed.inject(ProgrammingSubmissionService);
                component.exercise = exercise;
            });
    });

    afterEach(function () {
        // completely restore all fakes created through the sandbox
        sinon.restore();
    });

    it('should initialize for non programming exercise', fakeAsync(() => {
        const theExercise = { ...exercise, type: ExerciseType.FILE_UPLOAD };
        sinon.replace(exerciseService, 'find', sinon.fake.returns(of({ body: theExercise })));

        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const participation: StudentParticipation = { id: 1, student };
        sinon.replace(participationService, 'findAllParticipationsByExercise', sinon.fake.returns(of({ body: [participation] })));

        component.ngOnInit();
        tick();

        expect(component.isLoading).to.be.false;
        expect(component.participations.length).to.equal(1);
        expect(component.participations[0].id).to.equal(participation.id);
        expect(component.newManualResultAllowed).to.be.false;
        expect(component.presentationScoreEnabled).to.be.false;
    }));

    it('should initialize for programming exercise', fakeAsync(() => {
        const theExercise = { ...exercise, type: ExerciseType.PROGRAMMING };
        sinon.replace(exerciseService, 'find', sinon.fake.returns(of({ body: theExercise })));

        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const participation: StudentParticipation = { id: 1, student };
        sinon.replace(participationService, 'findAllParticipationsByExercise', sinon.fake.returns(of({ body: [participation] })));

        const submissionState: ProgrammingSubmissionStateObj = { participationId: participation.id!, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION };
        sinon.replace(submissionService, 'getSubmissionStateOfExercise', sinon.fake.returns(of(submissionState)));

        component.ngOnInit();
        tick();

        expect(component.isLoading).to.be.false;
        expect(component.participations.length).to.equal(1);
        expect(component.participations[0].id).to.equal(participation.id);
        expect(component.newManualResultAllowed).to.be.false;
        expect(component.presentationScoreEnabled).to.be.false;
        expect(component.exerciseSubmissionState).to.equal(submissionState);
    }));

    it('should format a dates correctly', () => {
        expect(component.formatDate(undefined)).to.equal('');

        const momentDate = moment();
        expect(component.formatDate(momentDate)).to.equal(momentDate.format('MMM DD YYYY, HH:mm:ss'));

        const date = new Date();
        const momentFromDate = moment(date);
        expect(component.formatDate(date)).to.equal(momentFromDate.format('MMM DD YYYY, HH:mm:ss'));
    });

    it('should format student login or team name from participation', () => {
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const participation: StudentParticipation = { student };
        expect(component.searchResultFormatter(participation)).to.equal(`${student.login} (${student.name})`);

        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        participation.student = undefined;
        participation.team = team;
        expect(component.searchResultFormatter(participation)).to.equal(formatTeamAsSearchResult(team));

        // Returns undefined for no student and no team
        participation.student = undefined;
        participation.team = undefined;
        expect(component.searchResultFormatter(participation)).to.be.undefined;
    });

    it('should return student login, team short name, or empty from participation', () => {
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        const participation: StudentParticipation = { student, team };

        expect(component.searchTextFromParticipation(participation)).to.be.equal(student.login);

        participation.student = undefined;
        expect(component.searchTextFromParticipation(participation)).to.be.equal(team.shortName);

        participation.team = undefined;
        expect(component.searchTextFromParticipation(participation)).to.be.empty;
    });

    it('should filter participation by prop', () => {
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        const participation: StudentParticipation = { id: 1, student, team };

        component.participationCriteria.filterProp = component.FilterProp.ALL;
        expect(component.filterParticipationByProp(participation)).to.be.true;

        // Returns true only if submission count is 0
        component.participationCriteria.filterProp = component.FilterProp.NO_SUBMISSIONS;
        expect(component.filterParticipationByProp(participation)).to.be.false;
        participation.submissionCount = 0;
        expect(component.filterParticipationByProp(participation)).to.be.true;
        participation.submissionCount = 1;
        expect(component.filterParticipationByProp(participation)).to.be.false;

        component.exerciseSubmissionState = {};
        component.participationCriteria.filterProp = component.FilterProp.FAILED;
        expect(component.filterParticipationByProp(participation)).to.be.false;

        // Test different submission states
        Object.values(ProgrammingSubmissionState).forEach((programmingSubmissionState) => {
            const submissionState: ProgrammingSubmissionStateObj = { participationId: participation.id!, submissionState: programmingSubmissionState };
            component.exerciseSubmissionState = { 1: submissionState };
            const expectedBoolean = programmingSubmissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION;
            expect(component.filterParticipationByProp(participation)).to.equal(expectedBoolean);
        });
    });

    describe('Presentation Score', () => {
        let updateSpy: SinonStub;

        beforeEach(() => {
            updateSpy = stub(participationService, 'update').returns(of());
        });

        const courseWithPresentationScore = {
            id: 1,
            title: 'Presentation Score',
            presentationScore: 2,
        } as Course;

        const courseWithoutPresentationScore = {
            id: 2,
            title: 'No Presentation Score',
            presentationScore: 0,
        } as Course;

        const exercise1 = {
            id: 1,
            title: 'Exercise 1',
            course: courseWithPresentationScore,
            presentationScoreEnabled: true,
            isAtLeastTutor: true,
        } as Exercise;

        const exercise2 = {
            id: 2,
            title: 'Exercise 2',
            course: courseWithoutPresentationScore,
            presentationScoreEnabled: false,
            isAtLeastTutor: true,
        } as Exercise;

        const participation = {
            student: { id: 1 },
            exercise: exercise1,
        } as StudentParticipation;

        it('should add a presentation score if the feature is enabled', () => {
            component.exercise = exercise1;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.addPresentation(participation);
            expect(updateSpy.callCount).to.equal(1);
            updateSpy.resetHistory();

            component.exercise = exercise2;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.addPresentation(participation);
            expect(updateSpy.callCount).to.equal(0);
        });

        it('should remove a presentation score if the feature is enabled', () => {
            component.exercise = exercise1;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.removePresentation(participation);
            expect(updateSpy.callCount).to.equal(1);
            updateSpy.resetHistory();

            component.exercise = exercise2;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.removePresentation(participation);
            expect(updateSpy.callCount).to.equal(0);
        });

        it('should check if the presentation score actions should be displayed', () => {
            component.exercise = exercise1;
            expect(component.checkPresentationScoreConfig()).to.be.true;

            component.exercise = exercise2;
            expect(component.checkPresentationScoreConfig()).to.be.false;
        });
    });
});
