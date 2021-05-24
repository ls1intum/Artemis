import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { JhiEventManager, NgJhipsterModule } from 'ng-jhipster';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { DifferencePipe } from 'ngx-moment';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import { mockTeam, MockTeamService } from '../../helpers/mocks/service/mock-team.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';
import { Exercise, ExerciseMode } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamParticipationTableComponent', () => {
    let comp: TeamParticipationTableComponent;
    let fixture: ComponentFixture<TeamParticipationTableComponent>;
    let debugElement: DebugElement;
    let teamService: TeamService;
    const course = {
        id: 123,
        title: 'Course Title',
        isAtLeastInstructor: true,
        isAtLeastEditor: true,
        endDate: moment().subtract(5, 'minutes'),
        courseArchivePath: 'some-path',
    } as Course;
    const exercise1 = {
        id: 1,
        mode: ExerciseMode.TEAM,
        teams: [mockTeam],
        course,
    } as Exercise;
    const exercise2 = {
        id: 2,
        mode: ExerciseMode.TEAM,
        teams: [mockTeam],
        course,
    } as Exercise;
    const exercise3 = {
        id: 3,
        mode: ExerciseMode.TEAM,
        teams: [mockTeam],
        course,
    } as Exercise;
    course.exercises = [exercise1, exercise2, exercise3];

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, FormsModule, NgJhipsterModule, NgbModule, ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisTeamModule],
            declarations: [],
            providers: [
                JhiEventManager,
                DifferencePipe,
                ExerciseService,
                ParticipationService,
                { provide: TeamService, useClass: MockTeamService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamParticipationTableComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                teamService = TestBed.inject(TeamService);
            });
    });

    beforeEach(fakeAsync(() => {
        const exercisesStub = sinon.stub(teamService, 'findCourseWithExercisesAndParticipationsForTeam');
        exercisesStub.returns(of(new HttpResponse({ body: course })));
    }));

    it('Exercises for one team are loaded correctly', fakeAsync(() => {
        comp.course = course;
        comp.team = mockTeam;

        comp.ngOnInit();
        tick();

        // Make sure that all 3 teams were received for exercise
        expect(comp.exercises).to.have.length(3);

        // Check that ngx-datatable is present
        const datatable = debugElement.query(By.css('jhi-data-table'));
        expect(datatable).to.exist;
    }));
});
