import { HttpResponse } from '@angular/common/http';
import { Directive, HostListener, Input } from '@angular/core';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintInteractionsComponent } from 'app/complaints/complaint-interactions.component';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { CourseExerciseSubmissionResultSimulationService } from 'app/course/manage/course-exercise-submission-result-simulation.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ProgrammingExerciseSimulationService } from 'app/exercises/programming/manage/services/programming-exercise-simulation.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
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
import * as chai from 'chai';
import { cloneDeep } from 'lodash-es';
import dayjs from 'dayjs';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, of } from 'rxjs';
import sinonChai from 'sinon-chai';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { MockParticipationWebsocketService } from '../../../helpers/mocks/service/mock-participation-websocket.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { MockRouter } from '../../../helpers/mocks/service/mock-route.service';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

chai.use(sinonChai);
const expect = chai.expect;

@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[routerLink]',
})
// tslint:disable-next-line:directive-class-suffix
class RouterLinkSpy {
    @Input()
    routerLink = '';

    constructor(private router: Router) {}

    @HostListener('click')
    onClick() {
        this.router.navigateByUrl(this.routerLink);
    }
}

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
    let getTeamPayloadMock: jest.SpyInstance;
    let mergeStudentParticipationMock: jest.SpyInstance;
    let subscribeForParticipationChangesMock: jest.SpyInstance;
    let complaintService: ComplaintService;
    const exercise = { id: 42, type: ExerciseType.TEXT, studentParticipations: [] } as unknown as Exercise;
    const route = { params: of({ courseId: 1, exerciseId: exercise.id }), queryParams: of({ welcome: '' }) };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                CourseExerciseDetailsComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(RouterOutlet),
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ResultHistoryComponent),
                MockComponent(ResultComponent),
                MockComponent(ComplaintInteractionsComponent),
                MockComponent(RatingComponent),
                RouterLinkSpy,
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(FaIconComponent),
                MockDirective(ExtensionPointDirective),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                MockProvider(ExerciseService),
                MockProvider(CourseManagementService),
                MockProvider(JhiWebsocketService),
                MockProvider(CourseScoreCalculationService),
                MockProvider(ParticipationService),
                MockProvider(SourceTreeService),
                MockProvider(GuidedTourService),
                MockProvider(CourseExerciseSubmissionResultSimulationService),
                MockProvider(ProgrammingExerciseSimulationService),
                MockProvider(TeamService),
                MockProvider(QuizExerciseService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(ComplaintService),
                MockProvider(ArtemisNavigationUtilService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseDetailsComponent);
                comp = fixture.componentInstance;

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
                getTeamPayloadMock = jest.spyOn(teamService, 'teamAssignmentUpdates', 'get');
                const teamAssignmentPayload = { exerciseId: 2, teamId: 2, studentParticipations: [] } as TeamAssignmentPayload;
                getTeamPayloadMock.mockReturnValue(() => Promise.resolve(of(teamAssignmentPayload)));

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
        expect(comp).to.be.ok;
        expect(comp.showWelcomeAlert).to.be.true;
        expect(comp.inProductionEnvironment).to.be.false;
        expect(comp.courseId).to.equal(1);
        expect(comp.exercise).to.deep.equal(exercise);
        expect(comp.hasMoreResults).to.be.false;
    }));

    it('should have student participations', fakeAsync(() => {
        const studentParticipation = new StudentParticipation();
        studentParticipation.student = new User(99);
        studentParticipation.submissions = [new TextSubmission()];
        studentParticipation.type = ParticipationType.STUDENT;
        const result = new Result();
        result.id = 1;
        result.completionDate = dayjs();
        studentParticipation.results = [result];
        studentParticipation.exercise = exercise;

        const exerciseDetail = { ...exercise, studentParticipations: [studentParticipation] };
        const exerciseDetailResponse = of({ body: exerciseDetail });

        // return initial participation for websocketService
        jest.spyOn(participationWebsocketService, 'getParticipationForExercise').mockReturnValue(studentParticipation);
        jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({} as EntityResponseType));

        // mock participationService, needed for team assignment
        participationService = TestBed.inject(ParticipationService);
        mergeStudentParticipationMock = jest.spyOn(participationService, 'mergeStudentParticipations');
        mergeStudentParticipationMock.mockReturnValue(studentParticipation);
        const changedParticipation = cloneDeep(studentParticipation);
        const changedResult = { ...result, id: 2 };
        changedParticipation.results = [changedResult];
        subscribeForParticipationChangesMock.mockReturnValue(new BehaviorSubject<Participation | undefined>(changedParticipation));

        fixture.detectChanges();
        tick(500);
        expect(comp).to.be.ok;

        // override mock to return exercise with participation
        getExerciseDetailsMock.mockReturnValue(exerciseDetailResponse);
        comp.loadExercise();
        fixture.detectChanges();
        expect(comp.courseId).to.equal(1);
        expect(comp.studentParticipation?.exercise?.id).to.equal(exerciseDetail.id);
        expect(comp.exercise!.studentParticipations![0].results![0]).to.deep.equal(changedResult);
        expect(comp.hasMoreResults).to.be.false;
        expect(comp.exerciseRatedBadge(result)).to.equal('bg-info');
    }));

    it('should not allow to publish a build plan for text exercises', () => {
        comp.exercise = { ...exercise };
        expect(comp.publishBuildPlanUrl()).to.be.undefined;
        expect(comp.projectKey()).to.be.undefined;
        expect(comp.buildPlanId(new StudentParticipation())).to.be.undefined;
    });

    it('should not be a quiz exercise', () => {
        comp.exercise = { ...exercise };
        expect(comp.quizExerciseStatus).to.be.undefined;
    });

    it('should simulate a submission', () => {
        const courseExerciseSubmissionResultSimulationService = fixture.debugElement.injector.get(CourseExerciseSubmissionResultSimulationService);
        jest.spyOn(courseExerciseSubmissionResultSimulationService, 'simulateSubmission').mockReturnValue(
            of(
                new HttpResponse({
                    body: new ProgrammingSubmission(),
                }),
            ),
        );
        comp.simulateSubmission();

        expect(comp.wasSubmissionSimulated).to.be.true;
    });

    it('should simulate a result', fakeAsync(() => {
        const result = new Result();
        result.participation = new StudentParticipation();
        comp.exercise = { id: 2 } as Exercise;
        const courseExerciseSubmissionResultSimulationService = fixture.debugElement.injector.get(CourseExerciseSubmissionResultSimulationService);
        jest.spyOn(courseExerciseSubmissionResultSimulationService, 'simulateResult').mockReturnValue(of({ body: result } as HttpResponse<Result>));
        comp.simulateResult();
        tick();
        flush();

        expect(comp.wasSubmissionSimulated).to.be.false;
        expect(comp.exercise?.participationStatus).to.equal(ParticipationStatus.EXERCISE_SUBMITTED);
    }));
});
