import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamsExportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-export-button.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import * as chai from 'chai';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { restore, SinonStub, stub } from 'sinon';
import sinonChai from 'sinon-chai';
import { mockTeams } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
chai.use(sinonChai);
const expect = chai.expect;

describe('TeamsExportButtonComponent', () => {
    let comp: TeamsExportButtonComponent;
    let fixture: ComponentFixture<TeamsExportButtonComponent>;
    let debugElement: DebugElement;
    let teamService: TeamService;

    function resetComponent() {
        comp.teams = mockTeams;
    }

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, MockModule(NgbModule), MockModule(FeatureToggleModule)],
                declarations: [TeamsExportButtonComponent, ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
                providers: [MockProvider(TeamService)],
            }).compileComponents();
        }),
    );
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsExportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        teamService = TestBed.inject(TeamService);
    });

    describe('exportTeams', () => {
        let exportTeamsStub: SinonStub;
        beforeEach(() => {
            resetComponent();
            exportTeamsStub = stub(teamService, 'exportTeams');
        });
        afterEach(() => {
            restore();
        });
        it('should call export teams from team service when called', () => {
            const button = debugElement.nativeElement.querySelector('button');
            button.click();
            expect(exportTeamsStub).to.have.been.called;
        });
    });
});
