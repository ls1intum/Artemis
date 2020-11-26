import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamsExportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-export-button.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import * as chai from 'chai';
import { JhiAlertService, JhiEventManager, NgJhipsterModule } from 'ng-jhipster';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { mockTeams, MockTeamService } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamsExportButtonComponent', () => {
    let comp: TeamsExportButtonComponent;
    let fixture: ComponentFixture<TeamsExportButtonComponent>;
    let debugElement: DebugElement;
    let teamService: TeamService;
    let alertService: JhiAlertService;

    function resetComponent() {
        comp.teams = mockTeams;
    }

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, ArtemisTeamModule],
                declarations: [],
                providers: [
                    JhiEventManager,
                    { provide: TeamService, useClass: MockTeamService },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                ],
            })
                .overrideTemplate(TeamsExportButtonComponent, '')
                .compileComponents();
        }),
    );
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsExportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        teamService = fixture.debugElement.injector.get(TeamService);
        alertService = debugElement.injector.get(JhiAlertService);
    });

    describe('exportTeams', () => {
        const event = { stopPropagation: () => {} } as MouseEvent;
        let eventSpy: SinonSpy;
        let exportTeamsStub: SinonStub;
        let alertServiceStub: SinonStub;
        beforeEach(() => {
            resetComponent();
            eventSpy = spy(event, 'stopPropagation');
            exportTeamsStub = stub(teamService, 'exportTeams');
            alertServiceStub = stub(alertService, 'error');
        });
        afterEach(() => {
            exportTeamsStub.restore();
            alertServiceStub.restore();
            eventSpy.restore();
        });
        it('should call export teams from team service when called', () => {
            comp.exportTeams(event);
            expect(eventSpy).to.have.been.called;
            expect(exportTeamsStub).to.have.been.calledWith(mockTeams);
        });
        it('should call alert service if team service fails', () => {
            exportTeamsStub.throws({ message: 'test message' });
            comp.exportTeams(event);
            expect(eventSpy).to.have.been.called;
            expect(exportTeamsStub).to.have.been.calledWith(mockTeams);
            expect(alertServiceStub).to.have.been.calledWith('artemisApp.team.errors.studentsWithoutRegistrationNumbers', { students: 'test message' });
        });
    });
});
