import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { CourseLearningGoalProgress, LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalManagementComponent } from 'app/course/learning-goals/learning-goal-management/learning-goal-management.component';
import { ActivatedRoute } from '@angular/router';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisTestModule } from '../../test.module';
import { LearningGoalCardStubComponent } from './learning-goal-card-stub.component';
import { NgbAccordion, NgbModal, NgbModalRef, NgbPanel, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { PrerequisiteImportComponent } from 'app/course/learning-goals/learning-goal-management/prerequisite-import.component';
import { Edge } from '@swimlane/ngx-graph';
import { Component } from '@angular/core';
import { CourseManagementTabBarComponent } from 'app/shared/course-management-tab-bar/course-management-tab-bar.component';

// eslint-disable-next-line @angular-eslint/component-selector
@Component({ selector: 'ngx-graph', template: '' })
class NgxGraphStubComponent {}

describe('LearningGoalManagementComponent', () => {
    let fixture: ComponentFixture<LearningGoalManagementComponent>;
    let component: LearningGoalManagementComponent;
    let learningGoalService: LearningGoalService;
    let modalService: NgbModal;

    let getAllForCourseSpy: any;
    let getCourseProgressSpy: any;
    let getAllPrerequisitesForCourseSpy: any;
    let getLearningGoalRelationsSpy: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                LearningGoalManagementComponent,
                LearningGoalCardStubComponent,
                NgxGraphStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(NgbPanel),
                MockComponent(NgbProgressbar),
                MockComponent(NgbAccordion),
                MockComponent(CourseManagementTabBarComponent),
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
                fixture = TestBed.createComponent(LearningGoalManagementComponent);
                component = fixture.componentInstance;
                learningGoalService = TestBed.inject(LearningGoalService);
                modalService = fixture.debugElement.injector.get(NgbModal);

                const learningGoal = new LearningGoal();
                const textUnit = new TextUnit();
                learningGoal.id = 1;
                learningGoal.description = 'test';
                learningGoal.lectureUnits = [textUnit];
                const courseLearningGoalProgress = new CourseLearningGoalProgress();
                courseLearningGoalProgress.learningGoalId = 1;
                courseLearningGoalProgress.numberOfStudents = 8;
                courseLearningGoalProgress.numberOfMasteredStudents = 5;
                courseLearningGoalProgress.averageStudentScore = 90;

                getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [learningGoal, { id: 5 } as LearningGoal],
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
                            body: [{ id: 3 } as LearningGoal],
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
        expect(component.learningGoals).toHaveLength(2);
    });

    it('should load prerequisites', () => {
        fixture.detectChanges();

        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(component.prerequisites).toHaveLength(1);
    });

    it('should delete learning goal', () => {
        const deleteSpy = jest.spyOn(learningGoalService, 'delete').mockReturnValue(of(new HttpResponse({ body: {}, status: 200 })));

        fixture.detectChanges();

        component.deleteLearningGoal(123);

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
            result: Promise.resolve({ id: 456 } as LearningGoal),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(modalRef);

        fixture.detectChanges();

        component.openImportModal();

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(modalService.open).toHaveBeenCalledWith(PrerequisiteImportComponent, { size: 'lg', backdrop: 'static' });
        expect(modalRef.componentInstance.disabledIds).toBeArrayOfSize(3);
        expect(modalRef.componentInstance.disabledIds).toContainAllValues([1, 5, 3]);
    });

    it('should create learning goal relation', () => {
        const createLearningGoalRelationSpy = jest
            .spyOn(learningGoalService, 'createLearningGoalRelation')
            .mockReturnValue(of(new HttpResponse({ body: new LearningGoal(), status: 200 })));
        component.tailLearningGoal = 123;
        component.headLearningGoal = 456;
        component.relationType = 'assumes';

        fixture.detectChanges();

        component.createRelation();
        expect(createLearningGoalRelationSpy).toHaveBeenCalledOnce();
        expect(createLearningGoalRelationSpy).toHaveBeenCalledWith(123, 456, 'assumes', 1);
    });

    it('should remove learning goal relation', () => {
        const removeLearningGoalRelationSpy = jest.spyOn(learningGoalService, 'removeLearningGoalRelation').mockReturnValue(of(new HttpResponse({ body: {}, status: 200 })));

        fixture.detectChanges();

        component.removeRelation({ source: '123', data: { id: 456 } } as Edge);
        expect(removeLearningGoalRelationSpy).toHaveBeenCalledOnce();
        expect(removeLearningGoalRelationSpy).toHaveBeenCalledWith(123, 456, 1);
    });
});
