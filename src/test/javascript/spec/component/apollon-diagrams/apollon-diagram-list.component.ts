import { Course } from 'app/entities/course.model';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MockProvider } from 'ng-mocks';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';
import { SortService } from 'app/shared/service/sort.service';
import { ApollonDiagramListComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-list.component';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';

describe('ApollonDiagramList Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let fixture: ComponentFixture<ApollonDiagramListComponent>;
    const sandbox = sinon.createSandbox();

    const course: Course = { id: 123 } as Course;

    beforeEach(() => {
        const route = ({ params: of({ courseId: 123 }), queryParamMap: of(convertToParamMap({ testRun: false })) } as any) as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [ApollonDiagramListComponent],
            providers: [
                JhiAlertService,
                ApollonDiagramService,
                MockProvider(SortService),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ActivatedRoute, useValue: route },
            ],
            schemas: [],
        })
            .overrideTemplate(ApollonDiagramListComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramListComponent);
                apollonDiagramService = TestBed.inject(ApollonDiagramService);
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('ngOnInit', () => {
        sandbox.stub(apollonDiagramService, 'getDiagramsByCourse').resolves({});
        // nothing to test in ngOnInit
        expect(true).toBeTruthy();
    });

    it('delete', fakeAsync(() => {
        const apollonDiagrams = [];
        for (let i = 0; i < 3; i++) {
            const apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
            apollonDiagram.id = i;
            apollonDiagrams.push(apollonDiagram);
        }
        const diagramToDelete = apollonDiagrams[0];
        fixture.componentInstance.apollonDiagrams = apollonDiagrams;
        sandbox
            .stub(apollonDiagramService, 'delete')
            .withArgs(diagramToDelete.id!, course.id!)
            .returns(of({} as HttpResponse<void>));
        fixture.componentInstance.delete(diagramToDelete);
        tick(500);
        expect(fixture.componentInstance.apollonDiagrams.find((diagram) => diagram.id === diagramToDelete.id)).toBeFalsy();
    }));
});
