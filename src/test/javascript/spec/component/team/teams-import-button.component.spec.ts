import { DebugElement } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamsImportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-button.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import * as chai from 'chai';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { restore, SinonStub, stub } from 'sinon';
import sinonChai from 'sinon-chai';
import { mockExercise, mockSourceTeams, mockTeams } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
chai.use(sinonChai);
const expect = chai.expect;

describe('TeamsImportButtonComponent', () => {
    let comp: TeamsImportButtonComponent;
    let fixture: ComponentFixture<TeamsImportButtonComponent>;
    let debugElement: DebugElement;
    let modalService: NgbModal;

    function resetComponent() {
        comp.teams = mockTeams;
        comp.exercise = mockExercise;
    }

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, MockModule(NgbModule), MockModule(FeatureToggleModule)],
                declarations: [TeamsImportButtonComponent, ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
                providers: [MockProvider(TeamService), MockProvider(NgbModal)],
            }).compileComponents();
        }),
    );
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsImportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        modalService = TestBed.inject(NgbModal);
    });

    describe('openTeamsImportDialog', () => {
        let modalServiceStub: SinonStub;
        let componentInstance: any;

        let teams: Team[] = [];

        beforeEach(() => {
            resetComponent();
            comp.save.subscribe((value: Team[]) => {
                teams = value;
            });
            componentInstance = { teams: [], exercise: undefined };
            const result = new Promise((resolve) => resolve(mockSourceTeams));
            modalServiceStub = stub(modalService, 'open').returns(<NgbModalRef>{ componentInstance, result });
        });
        afterEach(() => {
            restore();
        });
        it('should open teams import dialog when called', fakeAsync(() => {
            const button = debugElement.nativeElement.querySelector('button');
            button.click();
            expect(modalServiceStub).to.have.been.called;
            expect(componentInstance.exercise).to.deep.equal(mockExercise);
            expect(componentInstance.teams).to.deep.equal(mockTeams);
            tick(100);
            expect(teams).to.deep.equal(mockSourceTeams);
        }));
    });
});
