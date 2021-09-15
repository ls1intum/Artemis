import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpResponse } from '@angular/common/http';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';

chai.use(sinonChai);
const expect = chai.expect;

const resourceUrl = SERVER_API_URL + 'api';

describe('ApollonDiagramService', () => {
    let courseId: number;
    let apollonDiagram: ApollonDiagram;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [ApollonDiagramService],
        });
        courseId = 1;
        apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, courseId);
    });

    it('should be initialized', inject([ApollonDiagramService], (apollonDiagramService: ApollonDiagramService) => {
        expect(apollonDiagramService).to.be.ok;
    }));

    it('should create a diagram', fakeAsync(
        inject([ApollonDiagramService, HttpTestingController], (apollonDiagramService: ApollonDiagramService, backend: HttpTestingController) => {
            // Set up
            const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
            const responseObject = apollonDiagram;

            let response: HttpResponse<ApollonDiagram>;

            apollonDiagramService.create(apollonDiagram, courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram>) => {
                response = receivedResponse;
            });

            const requestWrapper = backend.expectOne({ url });
            requestWrapper.flush(responseObject);

            tick();

            expect(requestWrapper.request.method).to.equal('POST');
            expect(response!.body).to.equal(responseObject);
            expect(response!.status).to.equal(200);
        }),
    ));

    it('should update a diagram', fakeAsync(
        inject([ApollonDiagramService, HttpTestingController], (apollonDiagramService: ApollonDiagramService, backend: HttpTestingController) => {
            // Set up
            const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
            const responseObject = apollonDiagram;

            let response: HttpResponse<ApollonDiagram>;

            apollonDiagramService.update(apollonDiagram, courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram>) => {
                response = receivedResponse;
            });

            const requestWrapper = backend.expectOne({ url });
            requestWrapper.flush(responseObject);

            tick();

            expect(requestWrapper.request.method).to.equal('PUT');
            expect(response!.body).to.equal(responseObject);
            expect(response!.status).to.equal(200);
        }),
    ));

    it('should find a diagram', fakeAsync(
        inject([ApollonDiagramService, HttpTestingController], (apollonDiagramService: ApollonDiagramService, backend: HttpTestingController) => {
            // Set up
            const diagramId = 1;
            const url = `${resourceUrl}/course/${courseId}/apollon-diagrams/${diagramId}`;
            const responseObject = apollonDiagram;

            let response: HttpResponse<ApollonDiagram>;

            apollonDiagramService.find(diagramId, courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram>) => {
                response = receivedResponse;
            });

            const requestWrapper = backend.expectOne({ url });
            requestWrapper.flush(responseObject);

            tick();

            expect(requestWrapper.request.method).to.equal('GET');
            expect(response!.body).to.equal(responseObject);
            expect(response!.status).to.equal(200);
        }),
    ));

    it('should delete a diagram', fakeAsync(
        inject([ApollonDiagramService, HttpTestingController], (apollonDiagramService: ApollonDiagramService, backend: HttpTestingController) => {
            // Set up
            const diagramId = 1;
            const url = `${resourceUrl}/course/${courseId}/apollon-diagrams/${diagramId}`;
            const responseObject = apollonDiagram;

            let response: HttpResponse<void>;

            apollonDiagramService.delete(diagramId, courseId).subscribe((receivedResponse: HttpResponse<void>) => {
                response = receivedResponse;
            });

            const requestWrapper = backend.expectOne({ url });
            requestWrapper.flush(responseObject);

            tick();

            expect(requestWrapper.request.method).to.equal('DELETE');
            expect(response!.body).to.equal(responseObject);
            expect(response!.status).to.equal(200);
        }),
    ));

    it('should get diagrams by course', fakeAsync(
        inject([ApollonDiagramService, HttpTestingController], (apollonDiagramService: ApollonDiagramService, backend: HttpTestingController) => {
            // Set up
            const url = `${resourceUrl}/course/${courseId}/apollon-diagrams`;
            const responseObject = [apollonDiagram];

            let response: HttpResponse<ApollonDiagram[]>;

            apollonDiagramService.getDiagramsByCourse(courseId).subscribe((receivedResponse: HttpResponse<ApollonDiagram[]>) => {
                response = receivedResponse;
            });

            const requestWrapper = backend.expectOne({ url });
            requestWrapper.flush(responseObject);

            tick();

            expect(requestWrapper.request.method).to.equal('GET');
            expect(response!.body).to.equal(responseObject);
            expect(response!.status).to.equal(200);
        }),
    ));
});
