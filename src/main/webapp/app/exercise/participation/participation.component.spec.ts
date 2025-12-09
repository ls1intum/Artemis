import { ActivatedRoute, Params } from '@angular/router';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ParticipationComponent } from 'app/exercise/participation/participation.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { of, throwError } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { formatTeamAsSearchResult } from 'app/exercise/team/team.utils';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming/shared/services/programming-submission.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockProvider } from 'ng-mocks';
import { MockProgrammingSubmissionService } from 'test/helpers/mocks/service/mock-programming-submission.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { EventManager } from 'app/shared/service/event-manager.service';

describe('ParticipationComponent', () => {
    let component: ParticipationComponent;
    let componentFixture: ComponentFixture<ParticipationComponent>;
    let participationService: ParticipationService;
    let exerciseService: ExerciseService;
    let submissionService: ProgrammingSubmissionService;
    let alertService: AlertService;

    const exercise: Exercise = {
        numberOfAssessmentsOfCorrectionRounds: [],
        studentAssignedTeamIdComputed: false,
        id: 1,
        secondCorrectionEnabled: true,
    };

    const route = { params: of({ exerciseId: '1' } as Params) } as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: AlertService, useClass: MockAlertService },
                LocalStorageService,
                SessionStorageService,
                MockProvider(ExerciseService),
                MockProvider(ParticipationService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(EventManager),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ParticipationComponent);
                component = componentFixture.componentInstance;
                participationService = TestBed.inject(ParticipationService);
                exerciseService = TestBed.inject(ExerciseService);
                submissionService = TestBed.inject(ProgrammingSubmissionService);
                alertService = TestBed.inject(AlertService);
                component.exercise = exercise;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with exerciseId from route', () => {
        // @ts-ignore
        component.exercise = undefined;
        const exerciseFindStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        component.ngOnInit();

        expect(exerciseFindStub).toHaveBeenCalledExactlyOnceWith(exercise.id);
        expect(component.exercise).toEqual(exercise);
    });

    it('should initialize for non programming exercise', fakeAsync(() => {
        const theExercise = Object.assign({}, exercise, { type: ExerciseType.FILE_UPLOAD });
        const exerciseFindStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: theExercise })));

        const student: User = { id: 2, login: 'student', name: 'Max', internal: true };
        const participation: StudentParticipation = { id: 3, student };
        const participationFindStub = jest.spyOn(participationService, 'findAllParticipationsByExercise').mockReturnValue(of(new HttpResponse({ body: [participation] })));

        component.ngOnInit();
        tick();

        expect(component.isLoading).toBeFalse();
        expect(component.participations).toHaveLength(1);
        expect(component.participations[0].id).toBe(participation.id);
        expect(component.basicPresentationEnabled).toBeFalse();

        expect(exerciseFindStub).toHaveBeenCalledOnce();
        expect(exerciseFindStub).toHaveBeenCalledWith(theExercise.id);
        expect(participationFindStub).toHaveBeenCalledOnce();
        expect(participationFindStub).toHaveBeenCalledWith(theExercise.id, false);
    }));

    it('should initialize for programming exercise', fakeAsync(() => {
        const theExercise = Object.assign({}, exercise, { type: ExerciseType.PROGRAMMING });
        const exerciseFindStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: theExercise })));

        const student: User = { id: 2, login: 'student', name: 'Max', internal: true };
        const participation: StudentParticipation = { id: 3, student };
        const participationFindStub = jest.spyOn(participationService, 'findAllParticipationsByExercise').mockReturnValue(of(new HttpResponse({ body: [participation] })));

        const submissionState: ProgrammingSubmissionStateObj = {
            participationId: participation.id!,
            submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION,
        };
        const submissionGetStateStub = jest.spyOn(submissionService, 'getSubmissionStateOfExercise').mockReturnValue(of(submissionState));

        component.ngOnInit();
        tick();

        expect(component.isLoading).toBeFalse();
        expect(component.participations).toHaveLength(1);
        expect(component.participations[0].id).toBe(participation.id);
        expect(component.basicPresentationEnabled).toBeFalse();
        expect(component.exerciseSubmissionState).toEqual(submissionState);

        expect(exerciseFindStub).toHaveBeenCalledOnce();
        expect(exerciseFindStub).toHaveBeenCalledWith(theExercise.id);
        expect(participationFindStub).toHaveBeenCalledOnce();
        expect(participationFindStub).toHaveBeenCalledWith(theExercise.id, false);
        expect(submissionGetStateStub).toHaveBeenCalledOnce();
        expect(submissionGetStateStub).toHaveBeenCalledWith(theExercise.id);
    }));

    it('should format student login or team name from participation', () => {
        const student: User = { id: 1, login: 'student', name: 'Max', internal: true };
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
        const student: User = { id: 1, login: 'student', name: 'Max', internal: true };
        const team: Team = { name: 'Team', shortName: 'T', students: [student] };
        const participation: StudentParticipation = { id: 123, student, team };

        expect(component.searchTextFromParticipation(participation)).toBe(student.login);

        participation.student = undefined;
        expect(component.searchTextFromParticipation(participation)).toBe(team.shortName);

        participation.team = undefined;
        expect(component.searchTextFromParticipation(participation)).toHaveLength(0);
    });

    it('should filter participation by prop', () => {
        const student: User = { id: 1, login: 'student', name: 'Max', internal: true };
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
            const submissionState: ProgrammingSubmissionStateObj = {
                participationId: participation.id!,
                submissionState: programmingSubmissionState,
            };
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

    it('should error on save changedDueDate', () => {
        const errorSpy = jest.spyOn(alertService, 'error');
        jest.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(throwError(() => new HttpResponse({ body: null })));
        component.saveChangedDueDates();
        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should remove a participation from the change map when it has been deleted', fakeAsync(() => {
        const participation1 = participationWithIndividualDueDate(1, dayjs());
        component.changedIndividualDueDate(participation1);

        const expectedMap = new Map();
        expectedMap.set(1, participation1);
        expect(component.participationsChangedDueDate).toEqual(expectedMap);

        const deleteStub = jest.spyOn(participationService, 'delete').mockReturnValue(of(new HttpResponse()));

        component.deleteParticipation(1);
        tick();

        expect(deleteStub).toHaveBeenCalledOnce();
        expect(component.participationsChangedDueDate).toEqual(new Map());
    }));

    it('should update participation filter', async () => {
        jest.useFakeTimers();
        component.updateParticipationFilter(component.FilterProp.NO_SUBMISSIONS);
        jest.runAllTimers();
        expect(component.isLoading).toBeFalsy();
        expect(component.participationCriteria.filterProp).toBe(component.FilterProp.NO_SUBMISSIONS);
    });

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

        const courseWithBasicPresentations = {
            id: 1,
            title: 'Basic Presentations',
            presentationScore: 2,
        } as Course;

        const courseWithoutPresentations = {
            id: 2,
            title: 'No Presentations',
            presentationScore: 0,
        } as Course;

        const courseWithGradedPresentation = {
            id: 3,
            title: 'Graded Presentations',
            presentationScore: 0,
        } as Course;

        const exercise1 = {
            id: 1,
            title: 'Exercise 1',
            course: courseWithBasicPresentations,
            presentationScoreEnabled: true,
            isAtLeastTutor: true,
        } as Exercise;

        const exercise2 = {
            id: 2,
            title: 'Exercise 2',
            course: courseWithoutPresentations,
            presentationScoreEnabled: false,
            isAtLeastTutor: true,
        } as Exercise;

        const exercise3 = {
            id: 1,
            title: 'Exercise 3',
            course: courseWithGradedPresentation,
            presentationScoreEnabled: true,
            isAtLeastTutor: true,
        } as Exercise;

        const gradingScaleWithGradedPresentation = {
            presentationsNumber: 2,
            presentationsWeight: 20,
        } as GradeStepsDTO;

        const participation = {
            id: 123,
            student: { id: 1 },
            exercise: exercise1,
        } as StudentParticipation;

        it('should add a presentation score if basic presentations is enabled', fakeAsync(() => {
            component.exercise = exercise1;
            component.basicPresentationEnabled = component.checkBasicPresentationConfig();

            component.addBasicPresentation(participation);
            tick();

            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(exercise1, participation);
        }));

        it('should add a presentation score if graded presentations is enabled', fakeAsync(() => {
            component.exercise = exercise3;
            component.gradeStepsDTO = gradingScaleWithGradedPresentation;
            component.gradedPresentationEnabled = component.checkGradedPresentationConfig();

            participation.presentationScore = 20;
            component.addGradedPresentation(participation);
            tick();

            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(exercise3, participation);
        }));

        it('should not add an invalid presentation score if graded presentations is enabled', fakeAsync(() => {
            component.exercise = exercise3;
            component.gradeStepsDTO = gradingScaleWithGradedPresentation;
            component.gradedPresentationEnabled = component.checkGradedPresentationConfig();

            participation.presentationScore = 200;
            component.addGradedPresentation(participation);
            tick();

            expect(updateStub).not.toHaveBeenCalled();
        }));

        it('should not add a presentation score if presentations is disabled', fakeAsync(() => {
            component.exercise = exercise2;
            component.basicPresentationEnabled = component.checkBasicPresentationConfig();
            component.gradedPresentationEnabled = component.checkGradedPresentationConfig();

            component.addBasicPresentation(participation);
            tick();

            expect(updateStub).not.toHaveBeenCalled();
        }));

        it('should remove a presentation score if basic presentations is enabled', fakeAsync(() => {
            component.exercise = exercise1;
            component.basicPresentationEnabled = component.checkBasicPresentationConfig();

            component.removePresentation(participation);
            tick();

            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(exercise1, participation);
        }));

        it('should remove a presentation score if graded presentations is enabled', fakeAsync(() => {
            component.exercise = exercise3;
            component.gradeStepsDTO = gradingScaleWithGradedPresentation;
            component.gradedPresentationEnabled = component.checkGradedPresentationConfig();

            component.removePresentation(participation);
            tick();

            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(exercise3, participation);
        }));

        it('should do nothing on removal of a presentation score if presentations is disabled', fakeAsync(() => {
            component.exercise = exercise2;
            component.basicPresentationEnabled = component.checkBasicPresentationConfig();
            component.gradedPresentationEnabled = component.checkGradedPresentationConfig();

            component.removePresentation(participation);
            tick();

            expect(updateStub).not.toHaveBeenCalled();
        }));

        it('should check if the presentation score actions should be displayed', () => {
            component.exercise = exercise1;
            expect(component.checkBasicPresentationConfig()).toBeTrue();
            expect(component.checkGradedPresentationConfig()).toBeFalse();

            component.exercise = exercise2;
            expect(component.checkBasicPresentationConfig()).toBeFalse();
            expect(component.checkGradedPresentationConfig()).toBeFalse();

            component.exercise = exercise3;
            component.gradeStepsDTO = gradingScaleWithGradedPresentation;
            expect(component.checkBasicPresentationConfig()).toBeFalse();
            expect(component.checkGradedPresentationConfig()).toBeTrue();
        });

        it('should not add a presentation score if student gave max number of presentations', fakeAsync(() => {
            const errorResponse = new HttpErrorResponse({
                error: { errorKey: 'invalid.presentations.maxNumberOfPresentationsExceeded' },
                status: 400,
            });
            updateStub = jest.spyOn(participationService, 'update').mockReturnValue(throwError(() => errorResponse));

            component.exercise = exercise3;
            component.gradeStepsDTO = gradingScaleWithGradedPresentation;
            component.gradedPresentationEnabled = component.checkGradedPresentationConfig();

            participation.presentationScore = 40;
            component.addGradedPresentation(participation);
            tick();

            expect(participation.presentationScore).toBeUndefined();
            expect(updateStub).toHaveBeenCalledOnce();
            expect(updateStub).toHaveBeenCalledWith(exercise3, participation);
        }));
    });

    describe('getScoresRoute', () => {
        const course = {
            id: 1,
            title: 'Course 1',
        } as Course;

        it('should return the correct route for an exercise without an exam', () => {
            const exercise = {
                id: 10,
                title: 'Exercise 1',
                type: 'text',
                course: course,
            } as Exercise;

            const expectedRoute = ['/course-management', '1', 'text-exercises', '10', 'scores'];
            const result = component.getScoresRoute(exercise).map((part) => part.toString());
            expect(result).toEqual(expectedRoute);
        });

        it('should return the correct route for an exercise within an exam', () => {
            const exam = {
                id: 100,
                course: course,
            } as Exam;
            const exerciseGroup = {
                id: 50,
                exam: exam,
            } as ExerciseGroup;
            const exercise = {
                id: 20,
                title: 'Exercise 2',
                type: 'programming',
                exerciseGroup: exerciseGroup,
                course: undefined,
            } as Exercise;

            const expectedRoute = ['/course-management', '1', 'exams', '100', 'exercise-groups', '50', 'programming-exercises', '20', 'scores'];
            const result = component.getScoresRoute(exercise).map((part) => part.toString());
            expect(result).toEqual(expectedRoute);
        });
    });
});
