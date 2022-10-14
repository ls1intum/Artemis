import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupManagementDetailComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/detail/tutorial-group-management-detail.component';
import { TutorialGroupDetailStubComponent } from '../../../stubs/tutorial-group-detail-stub.component';
import { TutorialGroupRowButtonsStubComponent } from '../../../stubs/tutorial-group-row-buttons-stub.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';

describe('TutorialGroupManagementDetailComponent', () => {
    let fixture: ComponentFixture<TutorialGroupManagementDetailComponent>;
    let component: TutorialGroupManagementDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [TutorialGroupManagementDetailComponent, TutorialGroupRowButtonsStubComponent, TutorialGroupDetailStubComponent, LoadingIndicatorContainerStubComponent],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                mockedActivatedRoute(
                    {
                        tutorialGroupId: 1,
                    },
                    {},
                    {
                        course: {
                            id: 2,
                            isAtLeastInstructor: true,
                        },
                    },
                    {},
                ),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupManagementDetailComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should load tutorial group', () => {
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);

        const tutorialGroupOfResponse = generateExampleTutorialGroup({ id: 1 });

        const response: HttpResponse<TutorialGroup> = new HttpResponse({
            body: tutorialGroupOfResponse,
            status: 200,
        });

        const findByIdStub = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
        fixture.detectChanges();
        expect(component.tutorialGroup).toEqual(tutorialGroupOfResponse);
        expect(component.tutorialGroupId).toBe(1);
        expect(component.courseId).toBe(2);
        expect(findByIdStub).toHaveBeenCalledWith(2, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
    });

    it('should navigate to course management when course clicked callback called', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        fixture.detectChanges();
        component.onCourseClicked();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 2]);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should navigate to registrations when registration clicked callback called', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        fixture.detectChanges();
        component.onRegistrationsClicked();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 2, 'tutorial-groups-management', 1, 'registered-students']);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should navigate to tutorial group management when tutorial group delete callback called', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        fixture.detectChanges();
        component.onTutorialGroupDeleted();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 2, 'tutorial-groups-management']);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
