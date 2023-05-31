import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { Competency, CompetencyRelationError, CourseCompetencyProgress } from 'app/entities/competency.model';
import { CompetencyManagementComponent } from 'app/course/competencies/competency-management/competency-management.component';
import { ActivatedRoute } from '@angular/router';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisTestModule } from '../../test.module';
import { LearningGoalCardStubComponent } from './learning-goal-card-stub.component';
import { NgbModal, NgbModalRef, NgbPanel, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { PrerequisiteImportComponent } from 'app/course/competencies/competency-management/prerequisite-import.component';
import { Edge, Node } from '@swimlane/ngx-graph';
import { Component } from '@angular/core';
import { CompetencyImportComponent } from 'app/course/competencies/competency-management/competency-import.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { By } from '@angular/platform-browser';
import '@angular/localize/init';

// eslint-disable-next-line @angular-eslint/component-selector
@Component({ selector: 'ngx-graph', template: '' })
class NgxGraphStubComponent {}

describe('LearningGoalManagementComponent', () => {
    let fixture: ComponentFixture<CompetencyManagementComponent>;
    let component: CompetencyManagementComponent;
    let learningGoalService: CompetencyService;
    let modalService: NgbModal;

    let getAllForCourseSpy: any;
    let getCourseProgressSpy: any;
    let getAllPrerequisitesForCourseSpy: any;
    let getLearningGoalRelationsSpy: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), NgbProgressbar],
            declarations: [
                CompetencyManagementComponent,
                LearningGoalCardStubComponent,
                NgxGraphStubComponent,
                MockHasAnyAuthorityDirective,
                MockComponent(DocumentationButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(DeleteButtonDirective),
                MockDirective(NgbPanel),
            ],
            providers: [
                MockProvider(AccountService),
                MockProvider(AlertService),
                { provide: NgbModal, useClass: MockNgbModalService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: 1,
                            }),
                        },
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyManagementComponent);
                component = fixture.componentInstance;
                learningGoalService = TestBed.inject(CompetencyService);
                modalService = fixture.debugElement.injector.get(NgbModal);

                const learningGoal = new Competency();
                const textUnit = new TextUnit();
                learningGoal.id = 1;
                learningGoal.description = 'test';
                learningGoal.lectureUnits = [textUnit];
                const courseLearningGoalProgress = new CourseCompetencyProgress();
                courseLearningGoalProgress.competencyId = 1;
                courseLearningGoalProgress.numberOfStudents = 8;
                courseLearningGoalProgress.numberOfMasteredStudents = 5;
                courseLearningGoalProgress.averageStudentScore = 90;

                getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [learningGoal, { id: 5 } as Competency],
                            status: 200,
                        }),
                    ),
                );
                getCourseProgressSpy = jest.spyOn(learningGoalService, 'getCourseProgress').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: courseLearningGoalProgress,
                            status: 200,
                        }),
                    ),
                );
                getAllPrerequisitesForCourseSpy = jest.spyOn(learningGoalService, 'getAllPrerequisitesForCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [{ id: 3 } as Competency],
                            status: 200,
                        }),
                    ),
                );
                getLearningGoalRelationsSpy = jest.spyOn(learningGoalService, 'getLearningGoalRelations').mockReturnValue(of(new HttpResponse({ body: [], status: 200 })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load learning goal and associated progress', () => {
        fixture.detectChanges();

        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(getCourseProgressSpy).toHaveBeenCalledTimes(2);
        expect(getLearningGoalRelationsSpy).toHaveBeenCalledTimes(2);
        expect(component.competencies).toHaveLength(2);
    });

    it('should load prerequisites', () => {
        fixture.detectChanges();

        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(component.prerequisites).toHaveLength(1);
    });

    it('should delete learning goal', () => {
        const deleteSpy = jest.spyOn(learningGoalService, 'delete').mockReturnValue(of(new HttpResponse({ body: {}, status: 200 })));

        fixture.detectChanges();

        component.deleteCompetency(123);

        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith(123, 1);
    });

    it('should remove prerequisite', () => {
        const removePrerequisiteSpy = jest.spyOn(learningGoalService, 'removePrerequisite').mockReturnValue(of(new HttpResponse({ body: {}, status: 200 })));

        fixture.detectChanges();

        component.removePrerequisite(123);

        expect(removePrerequisiteSpy).toHaveBeenCalledOnce();
        expect(removePrerequisiteSpy).toHaveBeenCalledWith(123, 1);
    });

    it('should open import modal for prerequisites', () => {
        const modalRef = {
            result: Promise.resolve({ id: 456 } as Competency),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);

        fixture.detectChanges();

        component.openPrerequisiteSelectionModal();

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(modalService.open).toHaveBeenCalledWith(PrerequisiteImportComponent, { size: 'lg', backdrop: 'static' });
        expect(modalRef.componentInstance.disabledIds).toBeArrayOfSize(3);
        expect(modalRef.componentInstance.disabledIds).toContainAllValues([1, 5, 3]);
    });

    it('should open import modal for learning goals', () => {
        const modalRef = {
            result: Promise.resolve({ id: 456 } as Competency),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);

        fixture.detectChanges();

        const importButton = fixture.debugElement.query(By.css('#learningGoalImportButton'));
        importButton.nativeElement.click();

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(modalService.open).toHaveBeenCalledWith(CompetencyImportComponent, { size: 'lg', backdrop: 'static' });
        expect(modalRef.componentInstance.disabledIds).toBeArrayOfSize(3);
        expect(modalRef.componentInstance.disabledIds).toContainAllValues([1, 5, 3]);
    });

    it('should create learning goal relation', () => {
        const createLearningGoalRelationSpy = jest
            .spyOn(learningGoalService, 'createLearningGoalRelation')
            .mockReturnValue(of(new HttpResponse({ body: new Competency(), status: 200 })));
        component.tailCompetency = 123;
        component.headCompetency = 456;
        component.relationType = 'assumes';

        fixture.detectChanges();

        component.createRelation();
        expect(createLearningGoalRelationSpy).toHaveBeenCalledOnce();
        expect(createLearningGoalRelationSpy).toHaveBeenCalledWith(123, 456, 'assumes', 1);
    });

    it('should detect circles on relations', () => {
        const node1 = { id: '16', label: 'learningGoal1' } as Node;
        const node2 = { id: '17', label: 'learningGoal2' } as Node;
        const node3 = { id: '18', label: 'learningGoal3' } as Node;
        component.nodes = [node1, node2, node3];

        const edge1 = { id: 'edge1', source: '16', target: '17', label: 'EXTENDS' } as Edge;
        const edge2 = { id: 'edge2', source: '17', target: '18', label: 'MATCHES' } as Edge;
        component.edges = [edge1, edge2];

        component.tailCompetency = 18;
        component.headCompetency = 16;
        component.relationType = 'ASSUMES';

        component.validate();

        expect(component.relationError).toBe(CompetencyRelationError.CIRCULAR);
    });

    it('should prevent creating already existing relations', () => {
        const node1 = { id: '16', label: 'learningGoal1' } as Node;
        const node2 = { id: '17', label: 'learningGoal2' } as Node;
        component.nodes = [node1, node2];

        const edge1 = { id: 'edge1', source: '16', target: '17', label: 'EXTENDS' } as Edge;
        component.edges = [edge1];

        component.tailCompetency = 16;
        component.headCompetency = 17;
        component.relationType = 'EXTENDS';

        component.validate();

        expect(component.relationError).toBe(CompetencyRelationError.EXISTING);
    });

    it('should prevent creating self relations', () => {
        const node1 = { id: '16', label: 'learningGoal1' } as Node;
        const node2 = { id: '17', label: 'learningGoal2' } as Node;
        component.nodes = [node1, node2];

        component.tailCompetency = 16;
        component.headCompetency = 16;
        component.relationType = 'EXTENDS';

        component.validate();

        expect(component.relationError).toBe(CompetencyRelationError.SELF);
    });

    it('should remove learning goal relation', () => {
        const removeLearningGoalRelationSpy = jest.spyOn(learningGoalService, 'removeLearningGoalRelation').mockReturnValue(of(new HttpResponse({ body: {}, status: 200 })));

        fixture.detectChanges();

        component.removeRelation({ source: '123', data: { id: 456 } } as Edge);
        expect(removeLearningGoalRelationSpy).toHaveBeenCalledOnce();
        expect(removeLearningGoalRelationSpy).toHaveBeenCalledWith(123, 456, 1);
    });
});
