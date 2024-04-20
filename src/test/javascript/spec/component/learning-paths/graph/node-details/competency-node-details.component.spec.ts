import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockComponent, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CompetencyNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/competency-node-details.component';
import { Competency, CompetencyProgress, CompetencyTaxonomy } from 'app/entities/competency.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMocksModule } from '../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

describe('CompetencyNodeDetailsComponent', () => {
    let fixture: ComponentFixture<CompetencyNodeDetailsComponent>;
    let comp: CompetencyNodeDetailsComponent;
    let competencyService: CompetencyService;
    let findByIdStub: jest.SpyInstance;
    let competency: Competency;
    let competencyProgress: CompetencyProgress;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [CompetencyNodeDetailsComponent, MockComponent(CompetencyRingsComponent), MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyNodeDetailsComponent);
                comp = fixture.componentInstance;
                competency = new Competency();
                competency.id = 2;
                competency.title = 'Some arbitrary title';
                competency.description = 'Some description';
                competency.taxonomy = CompetencyTaxonomy.ANALYZE;
                competency.masteryThreshold = 50;
                competencyProgress = new CompetencyProgress();
                competencyProgress.progress = 80;
                competencyProgress.confidence = 70;

                competencyService = TestBed.inject(CompetencyService);
                findByIdStub = jest.spyOn(competencyService, 'findById').mockReturnValue(of(new HttpResponse({ body: competency })));
                comp.courseId = 1;
                comp.competencyId = competency.id;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load competency on init if not present', () => {
        fixture.detectChanges();
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(findByIdStub).toHaveBeenCalledWith(competency.id, 1);
        expect(comp.competency).toEqual(competency);
    });

    it('should not load competency on init if already present', () => {
        comp.competency = competency;
        comp.competencyProgress = competencyProgress;
        fixture.detectChanges();
        expect(findByIdStub).not.toHaveBeenCalled();
    });
});
