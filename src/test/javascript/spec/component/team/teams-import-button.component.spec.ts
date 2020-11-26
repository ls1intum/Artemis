import { DebugElement } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { TeamsImportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-button.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import * as chai from 'chai';
import { JhiEventManager, NgJhipsterModule } from 'ng-jhipster';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { mockExercise, mockSourceTeams, mockTeams } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';
import { Team } from 'app/entities/team.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamsImportButtonComponent', () => {
    // needed to make sure ace is defined
    // ace.acequire('ace/ext/modelist.js');
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
                imports: [
                    TranslateModule.forRoot(),
                    ArtemisTestModule,
                    FormsModule,
                    NgJhipsterModule,
                    NgbModule,
                    ArtemisSharedModule,
                    ArtemisSharedComponentModule,
                    ArtemisTeamModule,
                ],
                declarations: [],
                providers: [JhiEventManager, { provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }],
            })
                .overrideTemplate(TeamsImportButtonComponent, '')
                .compileComponents();
        }),
    );
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsImportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        modalService = debugElement.injector.get(NgbModal);
    });

    describe('openTeamsImportDialog', () => {
        const event = { stopPropagation: () => {} } as MouseEvent;
        let eventSpy: SinonSpy;

        let modalServiceStub: SinonStub;
        let componentInstance: any;

        let teams: Team[] = [];

        beforeEach(() => {
            resetComponent();
            eventSpy = spy(event, 'stopPropagation');
            comp.save.subscribe((value: Team[]) => {
                teams = value;
                console.log('save', teams);
            });
            componentInstance = { teams: [], exercise: undefined };
            const result = new Promise((resolve) => resolve(mockSourceTeams));
            modalServiceStub = stub(modalService, 'open').returns(<NgbModalRef>{ componentInstance, result });
        });
        afterEach(() => {
            modalServiceStub.restore();
            eventSpy.restore();
        });
        it('should open teams import dialog when called', fakeAsync(() => {
            comp.openTeamsImportDialog(event);
            expect(eventSpy).to.have.been.called;
            expect(modalServiceStub).to.have.been.called;
            expect(componentInstance.exercise).to.deep.equal(mockExercise);
            expect(componentInstance.teams).to.deep.equal(mockTeams);
            tick(100);
            console.log(teams);
            console.log(mockSourceTeams);
            expect(teams).to.deep.equal(mockSourceTeams);
        }));
    });
});
