import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { KnowledgeAreaDTO, KnowledgeAreasForImportDTO, StandardizedCompetencyDTO } from 'app/entities/competency/standardized-competency.model';
import { take } from 'rxjs';
import { CompetencyTaxonomy } from 'app/entities/competency.model';

describe('AdminStandardizedCompetencyService', () => {
    let adminStandardizedCompetencyService: AdminStandardizedCompetencyService;
    let httpTestingController: HttpTestingController;
    let defaultStandardizedCompetency: StandardizedCompetencyDTO;
    let defaultKnowledgeArea: KnowledgeAreaDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [],
        });

        adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultKnowledgeArea = {
            id: 1,
            title: 'Knowledge Area1',
        };
        defaultStandardizedCompetency = {
            id: 1,
            title: 'Standardized Competency1',
            taxonomy: CompetencyTaxonomy.ANALYZE,
            knowledgeAreaId: defaultKnowledgeArea.id,
        };
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should create competency', fakeAsync(() => {
        let actualCompetency = new HttpResponse<StandardizedCompetencyDTO>();
        const expectedCompetency = defaultStandardizedCompetency;
        const returnedFromService: StandardizedCompetencyDTO = { ...expectedCompetency };

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
        let actualCompetency = new HttpResponse<StandardizedCompetencyDTO>();
        const expectedCompetency = defaultStandardizedCompetency;
        const returnedFromService: StandardizedCompetencyDTO = { ...expectedCompetency };

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
        let actualKnowledgeArea = new HttpResponse<KnowledgeAreaDTO>();
        const expectedKnowledgeArea = defaultKnowledgeArea;
        const returnedFromService: KnowledgeAreaDTO = { ...expectedKnowledgeArea };

        adminStandardizedCompetencyService
            .createKnowledgeArea(expectedKnowledgeArea)
            .pipe(take(1))
            .subscribe((resp) => (actualKnowledgeArea = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(actualKnowledgeArea.body).toEqual(expectedKnowledgeArea);
    }));

    it('should update knowledge area', fakeAsync(() => {
        let actualCompetency = new HttpResponse<KnowledgeAreaDTO>();
        const expectedKnowledgeArea = defaultKnowledgeArea;
        const returnedFromService: KnowledgeAreaDTO = { ...expectedKnowledgeArea };

        adminStandardizedCompetencyService
            .updateKnowledgeArea(expectedKnowledgeArea)
            .pipe(take(1))
            .subscribe((resp) => (actualCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();

        expect(actualCompetency.body).toEqual(expectedKnowledgeArea);
    }));

    it('should delete knowledge area', fakeAsync(() => {
        let actualResult = new HttpResponse<void>();

        adminStandardizedCompetencyService
            .deleteKnowledgeArea(1)
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(actualResult.status).toBe(200);
    }));

    it('should import competencies', fakeAsync(() => {
        let actualResult = new HttpResponse<void>();
        const requestBody: KnowledgeAreasForImportDTO = {
            knowledgeAreas: [],
            sources: [],
        };

        adminStandardizedCompetencyService
            .importCompetencies(requestBody)
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush({ status: 200 });
        tick();

        expect(actualResult.status).toBe(200);
    }));
});
