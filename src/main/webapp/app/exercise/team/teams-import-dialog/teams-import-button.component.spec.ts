import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsImportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-import-button.component';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { mockExercise, mockSourceTeams, mockTeams } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-team.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
describe('TeamsImportButtonComponent', () => {
    let comp: TeamsImportButtonComponent;
    let fixture: ComponentFixture<TeamsImportButtonComponent>;
    let debugElement: DebugElement;
    let modalService: NgbModal;

    function resetComponent() {
        comp.teams = mockTeams;
        comp.exercise = mockExercise;
    }

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbModule), MockDirective(FeatureToggleDirective)],
            declarations: [TeamsImportButtonComponent, ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [MockProvider(TeamService), MockProvider(NgbModal)],
        }).compileComponents();
    }));
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsImportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        modalService = TestBed.inject(NgbModal);
    });

    describe('openTeamsImportDialog', () => {
        let modalServiceStub: jest.SpyInstance;
        let componentInstance: any;

        let teams: Team[] = [];

        beforeEach(() => {
            resetComponent();
            comp.save.subscribe((value: Team[]) => {
                teams = value;
            });
            componentInstance = { teams: [], exercise: undefined };
            const result = new Promise((resolve) => resolve(mockSourceTeams));
            modalServiceStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should open teams import dialog when called', fakeAsync(() => {
            const button = debugElement.nativeElement.querySelector('button');
            button.click();
            expect(modalServiceStub).toHaveBeenCalledOnce();
            expect(componentInstance.exercise).toEqual(mockExercise);
            expect(componentInstance.teams).toEqual(mockTeams);
            tick(100);
            expect(teams).toEqual(mockSourceTeams);
        }));
    });
});
