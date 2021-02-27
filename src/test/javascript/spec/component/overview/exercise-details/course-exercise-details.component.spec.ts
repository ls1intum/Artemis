import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipe } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { CourseExerciseSubmissionResultSimulationService } from 'app/course/manage/course-exercise-submission-result-simulation.service';
import { ProgrammingExerciseSimulationService } from 'app/exercises/programming/manage/services/programming-exercise-simulation.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { BehaviorSubject, of } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProgrammingExerciseStudentIdeActionsComponent } from 'app/overview/exercise-details/programming-exercise-student-ide-actions.component';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { Directive, HostListener, Input } from '@angular/core';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercises/shared/exercise-headers/header-exercise-page-with-details.component';
import { BuildPlanButtonDirective } from 'app/exercises/programming/shared/utils/build-plan-button.directive';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { TimeAgoPipe } from 'ngx-moment';
import { ComplaintInteractionsComponent } from 'app/complaints/complaint-interactions.component';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { SinonStub, stub } from 'sinon';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockParticipationWebsocketService } from '../../../helpers/mocks/service/mock-participation-websocket.service';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { User } from 'app/core/user/user.model';
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';

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
    let getProfileInfoStub: SinonStub;
    let getExerciseDetailsStub: SinonStub;
    let getTeamPayloadStub: SinonStub;
    let mergeStudentParticipationStub: SinonStub;
    const exercise = ({ id: 42, type: ExerciseType.TEXT, studentParticipations: [] } as unknown) as Exercise;
    const route = { params: of({ courseId: 1, exerciseId: exercise.id }), queryParams: of({ welcome: '' }) };

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisSharedModule],
            declarations: [
                CourseExerciseDetailsComponent,
                MockPipe(TranslatePipe),
                MockPipe(TimeAgoPipe),
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(OrionFilterDirective),
                MockDirective(BuildPlanButtonDirective),
                MockDirective(RouterOutlet),
                MockComponent(HeaderExercisePageWithDetailsComponent),
                MockComponent(ProgrammingExerciseStudentIdeActionsComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockComponent(ResultHistoryComponent),
                MockComponent(ResultComponent),
                MockComponent(ComplaintInteractionsComponent),
                MockComponent(RatingComponent),
                RouterLinkSpy,
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseDetailsComponent);
                comp = fixture.componentInstance;

                // stub profileService
                profileService = fixture.debugElement.injector.get(ProfileService);
                getProfileInfoStub = stub(profileService, 'getProfileInfo');
                const profileInfo = { inProduction: false } as ProfileInfo;
                const profileInfoSubject = new BehaviorSubject<ProfileInfo | null>(profileInfo);
                getProfileInfoStub.returns(profileInfoSubject);

                // stub exerciseService
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                getExerciseDetailsStub = stub(exerciseService, 'getExerciseDetails');
                getExerciseDetailsStub.returns(of({ body: exercise }));

                // stub teamService, needed for team assignment
                teamService = fixture.debugElement.injector.get(TeamService);
                getTeamPayloadStub = stub(teamService, 'teamAssignmentUpdates');
                const teamAssignmentPayload = { exerciseId: 2, teamId: 2, studentParticipations: [] } as TeamAssignmentPayload;
                getTeamPayloadStub.get(() => Promise.resolve(of(teamAssignmentPayload)));

                // stub participationService, needed for team assignment
                participationService = fixture.debugElement.injector.get(ParticipationService);
                mergeStudentParticipationStub = stub(participationService, 'mergeStudentParticipations');
            });
    }));

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
        result.completionDate = moment();
        studentParticipation.results = [result];
        const exerciseDetail = { ...exercise, studentParticipations: [studentParticipation] };
        const exerciseDetailReponse = of({ body: exerciseDetail });

        mergeStudentParticipationStub.returns(studentParticipation);

        fixture.detectChanges();
        tick(500);
        expect(comp).to.be.ok;

        // override stub to return exercise with participation
        getExerciseDetailsStub.returns(exerciseDetailReponse);
        comp.loadExercise();
        fixture.detectChanges();
        expect(comp.courseId).to.equal(1);
        expect(comp.studentParticipation?.exercise?.id).to.equal(exerciseDetail.id);
        expect(comp.exercise!.studentParticipations![0].results![0]).to.deep.equal(result);
        expect(comp.hasMoreResults).to.be.false;
        expect(comp.exerciseRatedBadge(result)).to.equal('badge-info');

        // has correct router link
        expect(comp.exerciseRouterLink).to.equal(`/course-management/1/text-exercises/42/assessment`);
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
        stub(courseExerciseSubmissionResultSimulationService, 'simulateSubmission').returns(
            of(
                new HttpResponse({
                    body: new ProgrammingSubmission(),
                }),
            ),
        );
        comp.simulateSubmission();

        expect(comp.wasSubmissionSimulated).to.be.true;
    });

    it('should simulate a result', () => {
        comp.exercise = { id: 2 } as Exercise;
        const courseExerciseSubmissionResultSimulationService = fixture.debugElement.injector.get(CourseExerciseSubmissionResultSimulationService);
        stub(courseExerciseSubmissionResultSimulationService, 'simulateResult').returns(
            of(
                new HttpResponse({
                    body: new Result(),
                }),
            ),
        );
        comp.simulateResult();

        expect(comp.wasSubmissionSimulated).to.be.false;
        expect(comp.exercise?.participationStatus).to.equal(ParticipationStatus.EXERCISE_SUBMITTED);
    });
});
