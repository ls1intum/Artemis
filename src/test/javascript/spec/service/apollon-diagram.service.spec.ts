import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';

const resourceUrl = SERVER_API_URL + 'api';

describe('ApollonDiagramService', () => {
    let courseId: number;
    let apollonDiagram: ApollonDiagram;
    let apollonDiagramService: ApollonDiagramService;
    let httpTestingController: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [ApollonDiagramService],
        })
            .compileComponents()
            .then(() => {
                apollonDiagramService = TestBed.inject(ApollonDiagramService);
                httpTestingController = TestBed.inject(HttpTestingController);
            });
        courseId = 1;
        apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, courseId);
    });

    it('should create a diagram', fakeAsync(() => {
        // Set up
        const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
        const responseObject = apollonDiagram;

        let response: HttpResponse<ApollonDiagram>;

        apollonDiagramService.create(apollonDiagram, courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram>) => {
            response = receivedResponse;
        });

        const requestWrapper = httpTestingController.expectOne({ url });
        requestWrapper.flush(responseObject);

        tick();

        expect(requestWrapper.request.method).toBe('POST');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    }));

    it('should update a diagram', fakeAsync(() => {
        // Set up
        const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
        const responseObject = apollonDiagram;

        let response: HttpResponse<ApollonDiagram>;

        apollonDiagramService.update(apollonDiagram, courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram>) => {
            response = receivedResponse;
        });

        const requestWrapper = httpTestingController.expectOne({ url });
        requestWrapper.flush(responseObject);

        tick();

        expect(requestWrapper.request.method).toBe('PUT');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    }));

    it('should find a diagram', fakeAsync(() => {
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

        tick();

        expect(requestWrapper.request.method).toBe('GET');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    }));

    it('should delete a diagram', fakeAsync(() => {
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

        tick();

        expect(requestWrapper.request.method).toBe('DELETE');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    }));

    it('should get diagrams by course', fakeAsync(() => {
        // Set up
        const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
        const responseObject = [apollonDiagram];

        let response: HttpResponse<ApollonDiagram[]>;

        apollonDiagramService.getDiagramsByCourse(courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram[]>) => {
            response = receivedResponse;
        });

        const requestWrapper = httpTestingController.expectOne({ url });
        requestWrapper.flush(responseObject);

        tick();

        expect(requestWrapper.request.method).toBe('GET');
        expect(response!.body).toEqual(responseObject);
        expect(response!.status).toBe(200);
    }));
});
