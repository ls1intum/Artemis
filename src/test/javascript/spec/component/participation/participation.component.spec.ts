import { ActivatedRoute, Params } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { NgModel } from '@angular/forms';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { Team } from 'app/entities/team.model';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/exercises/programming/participate/programming-submission.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockComponent, MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { defaultLongDateTimeFormat } from 'app/shared/pipes/artemis-date.pipe';
import { MockProgrammingSubmissionService } from '../../helpers/mocks/service/mock-programming-submission.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-submission-state.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-participate/team-students-list.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-trigger-build-button.component';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTestModule } from '../../test.module';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';

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
                MockComponent(DataTableComponent),
                MockComponent(FormDateTimePickerComponent),
                MockComponent(ProgrammingExerciseInstructorSubmissionStateComponent),
                MockComponent(ProgrammingExerciseInstructorTriggerBuildButtonComponent),
                MockComponent(TeamStudentsListComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(FeatureToggleDirective),
                MockDirective(NgModel),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
                TranslatePipeMock,
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                MockProvider(ExerciseService),
                MockProvider(ParticipationService),
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

        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max', internal: true };
        const participation: StudentParticipation = { id: 1, student };
        const participationFindStub = jest.spyOn(participationService, 'findAllParticipationsByExercise').mockReturnValue(of(new HttpResponse({ body: [participation] })));

        component.ngOnInit();
        tick();

        expect(component.isLoading).toBeFalse();
        expect(component.participations).toHaveLength(1);
        expect(component.participations[0].id).toBe(participation.id);
        expect(component.newManualResultAllowed).toBeFalse();
        expect(component.presentationScoreEnabled).toBeFalse();

        expect(exerciseFindStub).toHaveBeenCalledOnce();
        expect(exerciseFindStub).toHaveBeenCalledWith(theExercise.id);
        expect(participationFindStub).toHaveBeenCalledOnce();
        expect(participationFindStub).toHaveBeenCalledWith(participation.id, true);
    }));

    it('should initialize for programming exercise', fakeAsync(() => {
        const theExercise = { ...exercise, type: ExerciseType.PROGRAMMING };
        const exerciseFindStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: theExercise })));

        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max', internal: true };
        const participation: StudentParticipation = { id: 1, student };
        const participationFindStub = jest.spyOn(participationService, 'findAllParticipationsByExercise').mockReturnValue(of(new HttpResponse({ body: [participation] })));

        const submissionState: ProgrammingSubmissionStateObj = { participationId: participation.id!, submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION };
        const submissionGetStateStub = jest.spyOn(submissionService, 'getSubmissionStateOfExercise').mockReturnValue(of(submissionState));

        component.ngOnInit();
        tick();

        expect(component.isLoading).toBeFalse();
        expect(component.participations).toHaveLength(1);
        expect(component.participations[0].id).toBe(participation.id);
        expect(component.newManualResultAllowed).toBeFalse();
        expect(component.presentationScoreEnabled).toBeFalse();
        expect(component.exerciseSubmissionState).toEqual(submissionState);

        expect(exerciseFindStub).toHaveBeenCalledOnce();
        expect(exerciseFindStub).toHaveBeenCalledWith(theExercise.id);
        expect(participationFindStub).toHaveBeenCalledOnce();
        expect(participationFindStub).toHaveBeenCalledWith(participation.id, true);
        expect(submissionGetStateStub).toHaveBeenCalledOnce();
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
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max', internal: true };
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
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max', internal: true };
        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        const participation: StudentParticipation = { id: 123, student, team };

        expect(component.searchTextFromParticipation(participation)).toBe(student.login);

        participation.student = undefined;
        expect(component.searchTextFromParticipation(participation)).toBe(team.shortName);

        participation.team = undefined;
        expect(component.searchTextFromParticipation(participation)).toHaveLength(0);
    });

    it('should filter participation by prop', () => {
        const student: User = { guidedTourSettings: [], id: 1, login: 'student', name: 'Max', internal: true };
        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        const participation: StudentParticipation = { id: 1, student, team };

        component.participationCriteria.filterProp = component.FilterProp.ALL;
        expect(component.filterParticipationByProp(participation)).toBeTrue();

        // Returns true only if submission count is 0
        component.participationCriteria.filterProp = component.FilterProp.NO_SUBMISSIONS;
        expect(component.filterParticipationByProp(participation)).toBeFalse();
        participation.submissionCount = 0;
        expect(component.filterParticipationByProp(participation)).toBeTrue();
        participation.submissionCount = 1;
        expect(component.filterParticipationByProp(participation)).toBeFalse();

        component.exerciseSubmissionState = {};
        component.participationCriteria.filterProp = component.FilterProp.FAILED;
        expect(component.filterParticipationByProp(participation)).toBeFalse();

        // Test different submission states
        Object.values(ProgrammingSubmissionState).forEach((programmingSubmissionState) => {
            const submissionState: ProgrammingSubmissionStateObj = { participationId: participation.id!, submissionState: programmingSubmissionState };
            component.exerciseSubmissionState = { 1: submissionState };
            const expectedBoolean = programmingSubmissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION;
            expect(component.filterParticipationByProp(participation)).toBe(expectedBoolean);
        });
    });

    it('should add participations with updated due dates to the changed map', () => {
        expect(component.participationsChangedDueDate).toEqual(new Map());

        const participation1 = participationWithIndividualDueDate(1, dayjs());
        component.changedIndividualDueDate(participation1);
        expect(component.participationsChangedDueDate.get(1)).toEqual(participation1);

        // should overwrite the other one, as they have got the same id
        const participation1Copy = participationWithIndividualDueDate(1, dayjs().add(2, 'days'));
        component.changedIndividualDueDate(participation1Copy);

        const expectedMap = new Map();
        expectedMap.set(1, participation1Copy);
        expect(component.participationsChangedDueDate).toEqual(expectedMap);

        const participation2 = participationWithIndividualDueDate(2, undefined);
        component.changedIndividualDueDate(participation2);

        const expectedMap2 = new Map();
        expectedMap2.set(1, participation1Copy);
        expectedMap2.set(2, participation2);
        expect(component.participationsChangedDueDate).toEqual(expectedMap2);
    });

    it('should add participations to the changed map when removing their due date', () => {
        const participation1 = participationWithIndividualDueDate(1, dayjs());
        component.removeIndividualDueDate(participation1);

        const expectedMap = new Map();
        expectedMap.set(1, participationWithIndividualDueDate(1, undefined));

        expect(component.participationsChangedDueDate).toEqual(expectedMap);
    });

    it('should send all changed participations to the server when updating their due dates', fakeAsync(() => {
        const participation1 = participationWithIndividualDueDate(1, dayjs());
        const participation2 = participationWithIndividualDueDate(2, dayjs());
        const participation2NoDueDate = participationWithIndividualDueDate(2, undefined);

        component.exercise = new TextExercise(undefined, undefined);
        component.exercise.id = 20;
        component.participations = [participation1, participation2];

        component.changedIndividualDueDate(participation1);
        component.removeIndividualDueDate(participation2);

        const expectedMap = new Map();
        expectedMap.set(1, participation1);
        expectedMap.set(2, participationWithIndividualDueDate(2, undefined));
        expect(component.participationsChangedDueDate).toEqual(expectedMap);

        const updateDueDateStub = jest
            .spyOn(participationService, 'updateIndividualDueDates')
            .mockReturnValue(of(new HttpResponse({ body: [participation1, participation2NoDueDate] })));
        const expectedSent = [participation1, participation2NoDueDate];

        component.saveChangedDueDates();
        tick();

        expect(updateDueDateStub).toHaveBeenCalledOnce();
        expect(updateDueDateStub).toHaveBeenCalledWith(component.exercise, expectedSent);
        expect(component.participations).toEqual(expectedSent);
        expect(component.participationsChangedDueDate).toEqual(new Map());
        expect(component.isSaving).toBeFalse();
    }));

    it('should remove a participation from the change map when it has been deleted', fakeAsync(() => {
        const participation1 = participationWithIndividualDueDate(1, dayjs());
        component.changedIndividualDueDate(participation1);

        const expectedMap = new Map();
        expectedMap.set(1, participation1);
        expect(component.participationsChangedDueDate).toEqual(expectedMap);

        const deleteStub = jest.spyOn(participationService, 'delete').mockReturnValue(of(new HttpResponse()));

        component.deleteParticipation(1, {});
        tick();

        expect(deleteStub).toHaveBeenCalledOnce();
        expect(component.participationsChangedDueDate).toEqual(new Map());
    }));

    const participationWithIndividualDueDate = (participationId: number, dueDate?: dayjs.Dayjs): StudentParticipation => {
        const participation = new StudentParticipation();
        participation.id = participationId;
        participation.individualDueDate = dueDate;
        return participation;
    };

    describe('Presentation Score', () => {
        let updateStub: jest.SpyInstance;

        beforeEach(() => {
            updateStub = jest.spyOn(participationService, 'update').mockReturnValue(of(new HttpResponse({ body: new StudentParticipation() })));
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

        it('should add a presentation score if the feature is enabled', fakeAsync(() => {
            component.exercise = exercise1;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();

            component.addPresentation(participation);
            tick();

            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(exercise1, participation);
        }));

        it('should not add a presentation score if the feature is disabled', fakeAsync(() => {
            component.exercise = exercise2;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();

            component.addPresentation(participation);
            tick();

            expect(updateStub).not.toHaveBeenCalled();
        }));

        it('should remove a presentation score if the feature is enabled', fakeAsync(() => {
            component.exercise = exercise1;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();

            component.removePresentation(participation);
            tick();

            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(exercise1, participation);
        }));

        it('should do nothing on removal of a presentation score if the feature is disabled', fakeAsync(() => {
            component.exercise = exercise2;
            component.presentationScoreEnabled = component.checkPresentationScoreConfig();

            component.removePresentation(participation);
            tick();

            expect(updateStub).not.toHaveBeenCalled();
        }));

        it('should check if the presentation score actions should be displayed', () => {
            component.exercise = exercise1;
            expect(component.checkPresentationScoreConfig()).toBeTrue();

            component.exercise = exercise2;
            expect(component.checkPresentationScoreConfig()).toBeFalse();
        });
    });
});
