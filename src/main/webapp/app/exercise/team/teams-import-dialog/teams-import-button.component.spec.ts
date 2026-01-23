import { expect, vi } from 'vitest';
import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsImportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-import-button.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { mockExercise, mockSourceTeams, mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
describe('TeamsImportButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: TeamsImportButtonComponent;
    let fixture: ComponentFixture<TeamsImportButtonComponent>;
    let debugElement: DebugElement;
    let modalService: NgbModal;

    function resetComponent() {
        comp.teams = mockTeams;
        comp.exercise = mockExercise;
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), MockModule(NgbModule), MockDirective(FeatureToggleDirective)],
            providers: [MockProvider(TeamService), MockProvider(NgbModal), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
    });
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsImportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        modalService = TestBed.inject(NgbModal);
    });

    describe('openTeamsImportDialog', () => {
        let modalServiceStub: ReturnType<typeof vi.spyOn>;
        let componentInstance: any;

        let teams: Team[] = [];

        beforeEach(() => {
            resetComponent();
            comp.save.subscribe((value: Team[]) => {
                teams = value;
            });
            componentInstance = { teams: [], exercise: undefined };
            const result = new Promise((resolve) => resolve(mockSourceTeams));
            modalServiceStub = vi.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });
        });
        afterEach(() => {
            vi.restoreAllMocks();
        });
        it('should open teams import dialog when called', async () => {
            const button = debugElement.nativeElement.querySelector('button');
            button.click();
            expect(modalServiceStub).toHaveBeenCalledOnce();
            expect(componentInstance.exercise).toEqual(mockExercise);
            expect(componentInstance.teams).toEqual(mockTeams);
            await Promise.resolve();
            expect(teams).toEqual(mockSourceTeams);
        });
    });
});
