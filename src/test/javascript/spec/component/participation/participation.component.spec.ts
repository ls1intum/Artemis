import { ActivatedRoute, Params } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { NgModel } from '@angular/forms';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import dayjs from 'dayjs';
import { User } from 'app/core/user/user.model';
import { Team } from 'app/entities/team.model';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/exercises/programming/participate/programming-submission.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { defaultLongDateTimeFormat } from 'app/shared/pipes/artemis-date.pipe';
import { MockProgrammingSubmissionService } from '../../helpers/mocks/service/mock-programming-submission.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-submission-state.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-participate/team-students-list.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-trigger-build-button.component';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';

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
            imports: [ArtemisTestModule, MockModule(NgxDatatableModule)],
            declarations: [
                ParticipationComponent,
                MockComponent(AlertComponent),
                MockComponent(DataTableComponent),
                MockComponent(ProgrammingExerciseInstructorSubmissionStateComponent),
                MockComponent(ProgrammingExerciseInstructorTriggerBuildButtonComponent),
                MockComponent(TeamStudentsListComponent),
                MockComponent(DeleteButtonDirective),
                MockDirective(FeatureToggleDirective),
                MockDirective(NgModel),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(ParticipationService),
                MockProvider(ExerciseService),
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize for non programming exercise', fakeAsync(() => {
        const theExercise = { ...exercise, type: ExerciseType.FILE_UPLOAD };
        const exerciseFindStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: theExercise })));

        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const participation: StudentParticipation = { id: 1, student };
        const participationFindStub = jest.spyOn(participationService, 'findAllParticipationsByExercise').mockReturnValue(of(new HttpResponse({ body: [participation] })));

        component.ngOnInit();
        tick();

        expect(component.isLoading).toBe(false);
        expect(component.participations.length).toBe(1);
        expect(component.participations[0].id).toBe(participation.id);
        expect(component.newManualResultAllowed).toBe(false);
        expect(component.presentationScoreEnabled).toBe(false);

        expect(exerciseFindStub).toHaveBeenCalledTimes(1);
        expect(exerciseFindStub).toHaveBeenCalledWith(theExercise.id);
        expect(participationFindStub).toHaveBeenCalledTimes(1);
        expect(participationFindStub).toHaveBeenCalledWith(participation.id, true);
    }));

    it('should initialize for programming exercise', fakeAsync(() => {
        const theExercise = { ...exercise, type: ExerciseType.PROGRAMMING };
        const exerciseFindStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: theExercise })));

        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const participation: StudentParticipation = { id: 1, student };
        const participationFindStub = jest.spyOn(participationService, 'findAllParticipationsByExercise').mockReturnValue(of(new HttpResponse({ body: [participation] })));

        const submissionState: ProgrammingSubmissionStateObj = { participationId: participation.id!, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION };
        const submissionGetStateStub = jest.spyOn(submissionService, 'getSubmissionStateOfExercise').mockReturnValue(of(submissionState));

        component.ngOnInit();
        tick();

        expect(component.isLoading).toBe(false);
        expect(component.participations.length).toBe(1);
        expect(component.participations[0].id).toBe(participation.id);
        expect(component.newManualResultAllowed).toBe(false);
        expect(component.presentationScoreEnabled).toBe(false);
        expect(component.exerciseSubmissionState).toEqual(submissionState);

        expect(exerciseFindStub).toHaveBeenCalledTimes(1);
        expect(exerciseFindStub).toHaveBeenCalledWith(theExercise.id);
        expect(participationFindStub).toHaveBeenCalledTimes(1);
        expect(participationFindStub).toHaveBeenCalledWith(participation.id, true);
        expect(submissionGetStateStub).toHaveBeenCalledTimes(1);
        expect(submissionGetStateStub).toHaveBeenCalledWith(participation.id);
    }));

    it('should format a dates correctly', () => {
        expect(component.formatDate(undefined)).toBe('');

        const dayjsDate = dayjs();
        expect(component.formatDate(dayjsDate)).toEqual(dayjsDate.format(defaultLongDateTimeFormat));

        const date = new Date();
        const dayjsFromDate = dayjs(date);
        expect(component.formatDate(date)).toEqual(dayjsFromDate.format(defaultLongDateTimeFormat));
    });

    it('should format student login or team name from participation', () => {
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const participation: StudentParticipation = { id: 123, student };
        expect(component.searchResultFormatter(participation)).toBe(`${student.login} (${student.name})`);

        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        participation.student = undefined;
        participation.team = team;
        expect(component.searchResultFormatter(participation)).toBe(formatTeamAsSearchResult(team));

        // Returns undefined for no student and no team
        participation.student = undefined;
        participation.team = undefined;
        expect(component.searchResultFormatter(participation)).toBe('123');
    });

    it('should return student login, team short name, or empty from participation', () => {
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        const participation: StudentParticipation = { id: 123, student, team };

        expect(component.searchTextFromParticipation(participation)).toBe(student.login);

        participation.student = undefined;
        expect(component.searchTextFromParticipation(participation)).toBe(team.shortName);

        participation.team = undefined;
        expect(component.searchTextFromParticipation(participation)).toHaveLength(0);
    });

    it('should filter participation by prop', () => {
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max' };
        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        const participation: StudentParticipation = { id: 1, student, team };

        component.participationCriteria.filterProp = component.FilterProp.ALL;
        expect(component.filterParticipationByProp(participation)).toBe(true);

        // Returns true only if submission count is 0
        component.participationCriteria.filterProp = component.FilterProp.NO_SUBMISSIONS;
        expect(component.filterParticipationByProp(participation)).toBe(false);
        participation.submissionCount = 0;
        expect(component.filterParticipationByProp(participation)).toBe(true);
        participation.submissionCount = 1;
        expect(component.filterParticipationByProp(participation)).toBe(false);

        component.exerciseSubmissionState = {};
        component.participationCriteria.filterProp = component.FilterProp.FAILED;
        expect(component.filterParticipationByProp(participation)).toBe(false);

        // Test different submission states
        Object.values(ProgrammingSubmissionState).forEach((programmingSubmissionState) => {
            const submissionState: ProgrammingSubmissionStateObj = { participationId: participation.id!, submissionState: programmingSubmissionState };
            component.exerciseSubmissionState = { 1: submissionState };
            const expectedBoolean = programmingSubmissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION;
            expect(component.filterParticipationByProp(participation)).toBe(expectedBoolean);
        });
    });

    describe('Presentation Score', () => {
        let updateSpy: jest.SpyInstance;

        beforeEach(() => {
            updateSpy = jest.spyOn(participationService, 'update').mockReturnValue(of());
        });

        afterEach(() => {
            jest.restoreAllMocks();
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
            id: 123,
            student: { id: 1 },
            exercise: exercise1,
        } as StudentParticipation;

        it('should add a presentation score if the feature is enabled', () => {
            component.exercise = exercise1;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.addPresentation(participation);
            expect(updateSpy).toHaveBeenCalledTimes(1);
            expect(updateSpy).toHaveBeenCalledWith(exercise.id, participation);
        });

        it('should not add a presentation score if the feature is disabled', () => {
            component.exercise = exercise2;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.addPresentation(participation);
            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should remove a presentation score if the feature is enabled', () => {
            component.exercise = exercise1;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.removePresentation(participation);
            expect(updateSpy).toHaveBeenCalledTimes(1);
            expect(updateSpy).toHaveBeenCalledWith(exercise.id, participation);
        });

        it('should do nothing on removal of a presentation score if the feature is disabled', () => {
            component.exercise = exercise2;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();
            component.removePresentation(participation);
            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should check if the presentation score actions should be displayed', () => {
            component.exercise = exercise1;
            expect(component.checkPresentationScoreConfig()).toBe(true);

            component.exercise = exercise2;
            expect(component.checkPresentationScoreConfig()).toBe(false);
        });
    });
});
