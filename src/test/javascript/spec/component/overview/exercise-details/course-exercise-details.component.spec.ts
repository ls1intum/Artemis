import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of } from 'rxjs';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { MockParticipationWebsocketService } from '../../../helpers/mocks/service/mock-participation-websocket.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { LtiInitializerComponent } from 'app/overview/exercise-details/lti-initializer.component';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockCourseManagementService } from '../../../helpers/mocks/service/mock-course-management.service';

describe('CourseExerciseDetailsComponent', () => {
    let comp: CourseExerciseDetailsComponent;
    let fixture: ComponentFixture<CourseExerciseDetailsComponent>;
    let profileService: ProfileService;
    let exerciseService: ExerciseService;
    let teamService: TeamService;
    let participationService: ParticipationService;
    let participationWebsocketService: ParticipationWebsocketService;
    let getProfileInfoMock: jest.SpyInstance;
    let getExerciseDetailsMock: jest.SpyInstance;
    let mergeStudentParticipationMock: jest.SpyInstance;
    let subscribeForParticipationChangesMock: jest.SpyInstance;
    let complaintService: ComplaintService;
    const exercise = { id: 42, type: ExerciseType.TEXT, studentParticipations: [], course: {} } as unknown as Exercise;

    const modelingExercise = {
        id: 23,
        type: ExerciseType.MODELING,
        studentParticipations: [],
        exampleSolutionModel: '{ "key": "value" }',
        exampleSolutionExplanation: 'Solution<br>Explanation',
    } as unknown as ModelingExercise;

    const textExercise = {
        id: 24,
        type: ExerciseType.TEXT,
        studentParticipations: [],
        exampleSolution: 'Sample<br>Solution',
    } as unknown as TextExercise;

    const fileUploadExercise = {
        id: 25,
        type: ExerciseType.FILE_UPLOAD,
        studentParticipations: [],
        exampleSolution: 'Sample<br>Solution',
    } as unknown as FileUploadExercise;

    const programmingExercise = {
        id: 26,
        type: ExerciseType.PROGRAMMING,
        studentParticipations: [],
        exam: 'Sample<br>Solution',
        exampleSolutionPublished: true,
    } as unknown as ProgrammingExercise;

    const route = { params: of({ courseId: 1, exerciseId: exercise.id }), queryParams: of({ welcome: '' }) };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [
                CourseExerciseDetailsComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ResultHistoryComponent),
                MockComponent(ResultComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockComponent(RatingComponent),
                MockRouterLinkDirective,
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(FaIconComponent),
                MockDirective(ExtensionPointDirective),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
                MockComponent(LtiInitializerComponent),
                MockComponent(ModelingEditorComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                MockProvider(ExerciseService),
                MockProvider(ParticipationService),
                MockProvider(GuidedTourService),
                MockProvider(TeamService),
                MockProvider(QuizExerciseService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(ComplaintService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseDetailsComponent);
                comp = fixture.componentInstance;

                comp.studentParticipations = [];

                // mock profileService
                profileService = fixture.debugElement.injector.get(ProfileService);
                getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
                const profileInfo = { inProduction: false } as ProfileInfo;
                const profileInfoSubject = new BehaviorSubject<ProfileInfo | null>(profileInfo);
                getProfileInfoMock.mockReturnValue(profileInfoSubject);

                // mock exerciseService
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                getExerciseDetailsMock = jest.spyOn(exerciseService, 'getExerciseDetails');
                getExerciseDetailsMock.mockReturnValue(of({ body: exercise }));

                // mock teamService, needed for team assignment
                teamService = fixture.debugElement.injector.get(TeamService);
                const teamAssignmentPayload = { exerciseId: 2, teamId: 2, studentParticipations: [] } as TeamAssignmentPayload;
                jest.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockReturnValue(Promise.resolve(of(teamAssignmentPayload)));

                // mock participationService, needed for team assignment
                participationWebsocketService = fixture.debugElement.injector.get(ParticipationWebsocketService);
                subscribeForParticipationChangesMock = jest.spyOn(participationWebsocketService, 'subscribeForParticipationChanges');
                subscribeForParticipationChangesMock.mockReturnValue(new BehaviorSubject<Participation | undefined>(undefined));

                complaintService = TestBed.inject(ComplaintService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        tick(500);
        expect(comp.inProductionEnvironment).toBeFalse();
        expect(comp.courseId).toBe(1);
        expect(comp.exercise).toStrictEqual(exercise);
        expect(comp.hasMoreResults).toBeFalse();
    }));

    it('should have student participations', fakeAsync(() => {
        const studentParticipation = new StudentParticipation();
        studentParticipation.student = new User(99);
        studentParticipation.submissions = [new TextSubmission()];
        studentParticipation.type = ParticipationType.STUDENT;
        studentParticipation.id = 42;
        const result = new Result();
        result.id = 1;
        result.completionDate = dayjs();
        studentParticipation.results = [result];
        studentParticipation.exercise = exercise;

        const exerciseDetail = { ...exercise, studentParticipations: [studentParticipation] };
        const exerciseDetailResponse = of({ body: exerciseDetail });

        // return initial participation for websocketService
        jest.spyOn(participationWebsocketService, 'getParticipationsForExercise').mockReturnValue([studentParticipation]);
        jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({} as EntityResponseType));

        // mock participationService, needed for team assignment
        participationService = TestBed.inject(ParticipationService);
        mergeStudentParticipationMock = jest.spyOn(participationService, 'mergeStudentParticipations');
        mergeStudentParticipationMock.mockReturnValue([studentParticipation]);
        const changedParticipation = cloneDeep(studentParticipation);
        const changedResult = { ...result, id: 2 };
        changedParticipation.results = [changedResult];
        subscribeForParticipationChangesMock.mockReturnValue(new BehaviorSubject<Participation | undefined>(changedParticipation));

        fixture.detectChanges();
        tick(500);

        // override mock to return exercise with participation
        getExerciseDetailsMock.mockReturnValue(exerciseDetailResponse);
        mergeStudentParticipationMock.mockReturnValue([changedParticipation]);
        comp.loadExercise();
        fixture.detectChanges();
        expect(comp.courseId).toBe(1);
        expect(comp.studentParticipations?.[0].exercise?.id).toBe(exerciseDetail.id);
        expect(comp.exercise!.studentParticipations![0].results![0]).toStrictEqual(changedResult);
        expect(comp.hasMoreResults).toBeFalse();
        expect(comp.exerciseRatedBadge(result)).toBe('bg-info');
    }));

    it('should not be a quiz exercise', () => {
        comp.exercise = { ...exercise };
        expect(comp.quizExerciseStatus).toBeUndefined();
    });

    it('should fill & empty sample modeling solution', () => {
        comp.showIfExampleSolutionPresent({ ...modelingExercise });
        expect(comp.exampleSolution).toBeUndefined();
        expect(comp.exampleSolutionUML).toEqual(JSON.parse(modelingExercise.exampleSolutionModel!));
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeFalse();

        comp.showIfExampleSolutionPresent({ ...exercise });
        expect(comp.exampleSolution).toBeUndefined();
        expect(comp.exampleSolutionUML).toBeUndefined();
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeFalse();
    });

    it('should fill & empty sample text solution', () => {
        comp.showIfExampleSolutionPresent({ ...textExercise });
        expect(comp.exampleSolution).toBeDefined();
        expect(comp.exampleSolutionUML).toBeUndefined();
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeFalse();

        comp.showIfExampleSolutionPresent({ ...exercise });
        expect(comp.exampleSolution).toBeUndefined();
        expect(comp.exampleSolutionUML).toBeUndefined();
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeFalse();
    });

    it('should fill & empty sample file upload solution', () => {
        comp.showIfExampleSolutionPresent({ ...fileUploadExercise });
        expect(comp.exampleSolution).toBeDefined();
        expect(comp.exampleSolutionUML).toBeUndefined();
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeFalse();

        comp.showIfExampleSolutionPresent({ ...exercise });
        expect(comp.exampleSolution).toBeUndefined();
        expect(comp.exampleSolutionUML).toBeUndefined();
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeFalse();
    });

    it('should fill & empty sample programming exercise solution', () => {
        comp.showIfExampleSolutionPresent({ ...programmingExercise });
        expect(comp.exampleSolution).toBeUndefined();
        expect(comp.exampleSolutionUML).toBeUndefined();
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeTrue();

        comp.showIfExampleSolutionPresent({ ...programmingExercise, exampleSolutionPublished: false });
        expect(comp.exampleSolution).toBeUndefined();
        expect(comp.exampleSolutionUML).toBeUndefined();
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeFalse();

        comp.showIfExampleSolutionPresent({ ...exercise });
        expect(comp.exampleSolution).toBeUndefined();
        expect(comp.exampleSolutionUML).toBeUndefined();
        expect(comp.isProgrammingExerciseExampleSolutionPublished).toBeFalse();
    });
});
