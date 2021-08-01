import * as ace from 'brace';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { EventManager, NgJhipsterModule } from 'ng-jhipster';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamsComponent } from 'app/exercises/shared/team/teams.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { DifferencePipe } from 'ngx-moment';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { mockTeams, MockTeamService } from '../../helpers/mocks/service/mock-team.service';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { teamRoute } from 'app/exercises/shared/team/team.route';
import { RouterTestingModule } from '@angular/router/testing';
import { Router, convertToParamMap } from '@angular/router';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { MockParticipationService } from '../../helpers/mocks/service/mock-participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamsComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');

    let comp: TeamsComponent;
    let fixture: ComponentFixture<TeamsComponent>;
    let debugElement: DebugElement;
    let router: Router;

    const route = {
        params: of({ exerciseId: 1 }),
        snapshot: { queryParamMap: convertToParamMap({}) },
    } as any as ActivatedRoute;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                ArtemisTestModule,
                FormsModule,
                NgJhipsterModule,
                NgbModule,
                ArtemisSharedModule,
                ArtemisSharedComponentModule,
                ArtemisTeamModule,
                RouterTestingModule.withRoutes([teamRoute[0]]),
            ],
            declarations: [],
            providers: [
                EventManager,
                DifferencePipe,
                { provide: TeamService, useClass: MockTeamService },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                fixture.ngZone!.run(() => {
                    router.initialNavigation();
                });
            });
    });

    it('Teams are loaded correctly', fakeAsync(() => {
        comp.ngOnInit();
        tick();

        // Make sure that all 3 teams were received for exercise
        expect(comp.teams).to.have.length(mockTeams.length);

        // Check that ngx-datatable is present
        const datatable = debugElement.query(By.css('jhi-data-table'));
        expect(datatable).to.exist;
    }));
});
