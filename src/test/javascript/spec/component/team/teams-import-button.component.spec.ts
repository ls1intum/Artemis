import { DebugElement } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Team } from 'app/entities/team.model';
import { TeamsImportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-button.component';
import * as chai from 'chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { mockExercise, mockSourceTeams, mockTeams } from '../../helpers/mocks/service/mock-team.service';
import { config } from './teams-import-dialog.component.spec';

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
            TestBed.configureTestingModule(config).overrideTemplate(TeamsImportButtonComponent, '').compileComponents();
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
            expect(teams).to.deep.equal(mockSourceTeams);
        }));
    });
});
