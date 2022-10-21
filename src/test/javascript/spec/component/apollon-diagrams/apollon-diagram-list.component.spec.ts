import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { Course } from 'app/entities/course.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ApollonDiagramListComponent } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram-list.component';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { SortService } from 'app/shared/service/sort.service';
import { isEqual } from 'lodash-es';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('ApollonDiagramList Component', () => {
    let apollonDiagramService: ApollonDiagramService;
    let courseService: CourseManagementService;
    let fixture: ComponentFixture<ApollonDiagramListComponent>;

    const course: Course = { id: 123 } as Course;

    beforeEach(() => {
        const route = { params: of({ courseId: 123 }), snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [ApollonDiagramListComponent],
            providers: [
                AlertService,
                ApollonDiagramService,
                MockProvider(SortService),
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(CourseManagementService),
                MockProvider(AccountService),
            ],
            schemas: [],
        })
            .overrideTemplate(ApollonDiagramListComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramListComponent);
                const injector = fixture.debugElement.injector;
                apollonDiagramService = injector.get(ApollonDiagramService);
                courseService = injector.get(CourseManagementService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngOnInit', () => {
        const apollonDiagrams: ApollonDiagram[] = [new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!), new ApollonDiagram(UMLDiagramType.ActivityDiagram, course.id!)];
        const diagramResponse: HttpResponse<ApollonDiagram[]> = new HttpResponse({ body: apollonDiagrams });
        const courseResponse: HttpResponse<Course> = new HttpResponse({ body: course });

        jest.spyOn(apollonDiagramService, 'getDiagramsByCourse').mockReturnValue(of(diagramResponse));
        jest.spyOn(courseService, 'find').mockReturnValue(of(courseResponse));

        // test
        fixture.componentInstance.ngOnInit();
        expect(isEqual(fixture.componentInstance.apollonDiagrams, apollonDiagrams)).toBeTruthy();
    });

    it('delete', () => {
        // setup
        const response: HttpResponse<void> = new HttpResponse();
        jest.spyOn(apollonDiagramService, 'delete').mockReturnValue(of(response));

        const apollonDiagrams = [];
        for (let i = 0; i < 3; i++) {
            const apollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);
            apollonDiagram.id = i;
            apollonDiagrams.push(apollonDiagram);
        }

        const diagramToDelete = apollonDiagrams[0];
        fixture.componentInstance.apollonDiagrams = apollonDiagrams;
        fixture.componentInstance.delete(diagramToDelete);
        expect(fixture.componentInstance.apollonDiagrams.find((diagram) => diagram.id === diagramToDelete.id)).toBeFalsy();
    });
});
