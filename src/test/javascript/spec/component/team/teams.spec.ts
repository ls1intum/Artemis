import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { JhiEventManager, NgJhipsterModule } from 'ng-jhipster';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/alert/alert.service';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamsComponent } from 'app/exercises/shared/team/teams.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { DifferencePipe } from 'ngx-moment';
import { SortByPipe } from 'app/shared/pipes/sort-by.pipe';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { MockCookieService } from '../../mocks/mock-cookie.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { mockTeams, MockTeamService } from '../../mocks/mock-team.service';
import { MockExerciseService } from '../../mocks/mock-exercise.service';
import { teamRoute } from 'app/exercises/shared/team/team.route.ts';
import { RouterTestingModule } from '@angular/router/testing';
import { Router } from '@angular/router';
import { Location } from '@angular/common';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { MockParticipationService } from '../../mocks/mock-participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../mocks/mock-account.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamsComponent', () => {
    let comp: TeamsComponent;
    let fixture: ComponentFixture<TeamsComponent>;
    let service: TeamService;
    let debugElement: DebugElement;
    let router: Router;
    let location: Location;
    let ngbActiveModal: NgbActiveModal;

    const route =
        ({
            params: of({ exerciseId: 1 }),
        } as
            any) as
        ActivatedRoute;

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
                SortByModule,
                RouterTestingModule.withRoutes([teamRoute[0]]),
            ],
            declarations: [],
            providers: [
                JhiEventManager,
                DifferencePipe,
                { provide: AlertService, useClass: MockAlertService },
                { provide: TeamService, useClass: MockTeamService },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: SortByPipe, useClass: SortByPipe },
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
                location = debugElement.injector.get(Location);
                service = debugElement.injector.get(TeamService);
                ngbActiveModal = TestBed.inject(NgbActiveModal);
                router.initialNavigation();
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
