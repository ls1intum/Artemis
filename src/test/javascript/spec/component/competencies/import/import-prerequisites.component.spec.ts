import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ImportPrerequisitesComponent } from 'app/course/competencies/import-competencies/import-prerequisites.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule } from 'app/forms/forms.module';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { ImportCompetenciesTableComponent } from 'app/course/competencies/import-competencies/import-competencies-table.component';
import { CompetencySearchComponent } from 'app/course/competencies/import-competencies/competency-search.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';

describe('ImportPrerequisitesComponent', () => {
    let componentFixture: ComponentFixture<ImportPrerequisitesComponent>;
    let component: ImportPrerequisitesComponent;
    let prerequisiteService: PrerequisiteService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ImportPrerequisitesComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ButtonComponent),
                MockComponent(ImportCompetenciesTableComponent),
                MockComponent(CompetencySearchComponent),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 1 }),
                },
                { provide: Router, useClass: MockRouter },
                MockProvider(CompetencyService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ImportPrerequisitesComponent);
                component = componentFixture.componentInstance;
                prerequisiteService = TestBed.inject(PrerequisiteService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should import prerequisites on submit', () => {
        const importBulkSpy = jest.spyOn(prerequisiteService, 'importPrerequisites').mockReturnValue(
            of([
                { id: 1, title: 'competency1' },
                { id: 1, title: 'competency2' },
            ]),
        );
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.onSubmit();

        expect(importBulkSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalled();
    });
});
