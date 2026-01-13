import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApollonDiagramService } from 'app/quiz/manage/apollon-diagrams/services/apollon-diagram.service';
import { ApollonDiagram } from 'app/modeling/shared/entities/apollon-diagram.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { UMLDiagramType } from '@ls1intum/apollon';

const resourceUrl = 'api/modeling';

describe('ApollonDiagramService', () => {
    setupTestBed({ zoneless: true });

    let courseId: number;
    let apollonDiagram: ApollonDiagram;
    let apollonDiagramService: ApollonDiagramService;
    let httpTestingController: HttpTestingController;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), ApollonDiagramService],
        }).compileComponents();

        apollonDiagramService = TestBed.inject(ApollonDiagramService);
        httpTestingController = TestBed.inject(HttpTestingController);
        courseId = 1;
        apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, courseId);
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should create a diagram', () => {
        // Set up
        const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
        const responseObject = apollonDiagram;

        let response: HttpResponse<ApollonDiagram>;

        apollonDiagramService.create(apollonDiagram, courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram>) => {
            response = receivedResponse;
        });

        const requestWrapper = httpTestingController.expectOne({ url });
        requestWrapper.flush(responseObject);

        expect(requestWrapper.request.method).toBe('POST');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    });

    it('should update a diagram', () => {
        // Set up
        const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
        const responseObject = apollonDiagram;

        let response: HttpResponse<ApollonDiagram>;

        apollonDiagramService.update(apollonDiagram, courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram>) => {
            response = receivedResponse;
        });

        const requestWrapper = httpTestingController.expectOne({ url });
        requestWrapper.flush(responseObject);

        expect(requestWrapper.request.method).toBe('PUT');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    });

    it('should find a diagram', () => {
        // Set up
        const diagramId = 1;
        const url = `${resourceUrl}/course/${courseId}/apollon-diagrams/${diagramId}`;
        const responseObject = apollonDiagram;

        let response: HttpResponse<ApollonDiagram>;

        apollonDiagramService.find(diagramId, courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram>) => {
            response = receivedResponse;
        });

        const requestWrapper = httpTestingController.expectOne({ url });
        requestWrapper.flush(responseObject);

        expect(requestWrapper.request.method).toBe('GET');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    });

    it('should delete a diagram', () => {
        // Set up
        const diagramId = 1;
        const url = `${resourceUrl}/course/${courseId}/apollon-diagrams/${diagramId}`;
        const responseObject = apollonDiagram;

        let response: HttpResponse<void>;

        apollonDiagramService.delete(diagramId, courseId).subscribe((receivedResponse: HttpResponse<void>) => {
            response = receivedResponse;
        });

        const requestWrapper = httpTestingController.expectOne({ url });
        requestWrapper.flush(responseObject);

        expect(requestWrapper.request.method).toBe('DELETE');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    });

    it('should get diagrams by course', () => {
        // Set up
        const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
        const responseObject = [apollonDiagram];

        let response: HttpResponse<ApollonDiagram[]>;

        apollonDiagramService.getDiagramsByCourse(courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram[]>) => {
            response = receivedResponse;
        });

        const requestWrapper = httpTestingController.expectOne({ url });
        requestWrapper.flush(responseObject);

        expect(requestWrapper.request.method).toBe('GET');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    });
});
