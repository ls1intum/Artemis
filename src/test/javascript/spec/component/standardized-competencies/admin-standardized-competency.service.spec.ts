import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { KnowledgeArea, StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';
import { take } from 'rxjs';
import { CompetencyTaxonomy } from 'app/entities/competency.model';
describe('AdminStandardizedCompetencyService', () => {
    let adminStandardizedCompetencyService: AdminStandardizedCompetencyService;
    let httpTestingController: HttpTestingController;
    let defaultStandardizedCompetency: StandardizedCompetency;
    let defaultKnowledgeArea: KnowledgeArea;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [],
        });

        adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultStandardizedCompetency = {
            id: 1,
            title: 'Standardized Competency1',
            taxonomy: CompetencyTaxonomy.ANALYZE,
        };
        defaultKnowledgeArea = {
            id: 1,
            title: 'Knowledge Area1',
        };
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should create competency', fakeAsync(() => {
        let actualCompetency = new HttpResponse<StandardizedCompetency>();
        const expectedCompetency = defaultStandardizedCompetency;
        const returnedFromService: StandardizedCompetency = { ...expectedCompetency };

        adminStandardizedCompetencyService
            .createStandardizedCompetency(expectedCompetency)
            .pipe(take(1))
            .subscribe((resp) => (actualCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(actualCompetency.body).toEqual(expectedCompetency);
    }));

    it('should update competency', fakeAsync(() => {
        let actualCompetency = new HttpResponse<StandardizedCompetency>();
        const expectedCompetency = defaultStandardizedCompetency;
        const returnedFromService: StandardizedCompetency = { ...expectedCompetency };

        adminStandardizedCompetencyService
            .updateStandardizedCompetency(expectedCompetency)
            .pipe(take(1))
            .subscribe((resp) => (actualCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();

        expect(actualCompetency.body).toEqual(expectedCompetency);
    }));

    it('should delete competency', fakeAsync(() => {
        let actualResult = new HttpResponse<void>();

        adminStandardizedCompetencyService
            .deleteStandardizedCompetency(1)
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(actualResult.status).toBe(200);
    }));

    it('should create knowledge area', fakeAsync(() => {
        let actualKnowledgeArea = new HttpResponse<KnowledgeArea>();
        const expectedKnowledgeArea = defaultKnowledgeArea;
        const returnedFromService: KnowledgeArea = { ...expectedKnowledgeArea };

        adminStandardizedCompetencyService
            .createKnowledgeArea(expectedKnowledgeArea)
            .pipe(take(1))
            .subscribe((resp) => (actualKnowledgeArea = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(actualKnowledgeArea.body).toEqual(expectedKnowledgeArea);
    }));
});
