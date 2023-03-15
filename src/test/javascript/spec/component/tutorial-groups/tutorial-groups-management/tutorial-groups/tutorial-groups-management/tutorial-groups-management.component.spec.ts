import { HttpResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdownModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';

import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { generateExampleTutorialGroupsConfiguration } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupRowButtonsStubComponent } from '../../../stubs/tutorial-group-row-buttons-stub.component';
import { TutorialGroupsTableStubComponent } from '../../../stubs/tutorial-groups-table-stub.component';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { Course } from 'app/entities/course.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({ selector: 'jhi-tutorial-groups-course-information', template: '' })
class MockTutorialGroupsCourseInformationComponent {
    @Input()
    tutorialGroups: TutorialGroup[] = [];
}
@Component({
    selector: 'jhi-tutorial-groups-import-button',
    template: '',
})
class MockTutorialGroupsImportButtonComponent {
    @Input() courseId: number;
    @Output() importFinished: EventEmitter<void> = new EventEmitter();
}

describe('TutorialGroupsManagementComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsManagementComponent>;
    let component: TutorialGroupsManagementComponent;
    const configuration = generateExampleTutorialGroupsConfiguration({});
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true } as Course;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    let tutorialGroupsService: TutorialGroupsService;
    let configurationService: TutorialGroupsConfigurationService;
    let getAllOfCourseSpy: jest.SpyInstance;
    let getOneOfCourseSpy: jest.SpyInstance;
    let navigateSpy: jest.SpyInstance;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbDropdownModule, MockDirective(NgbTooltip)],
            declarations: [
                TutorialGroupsManagementComponent,
                MockTutorialGroupsCourseInformationComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupsTableStubComponent,
                TutorialGroupRowButtonsStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockTutorialGroupsImportButtonComponent,
            ],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {},
                    {},
                    {
                        course,
                    },
                    {},
                ),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsManagementComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });

                tutorialGroupsService = TestBed.inject(TutorialGroupsService);
                getAllOfCourseSpy = jest.spyOn(tutorialGroupsService, 'getAllForCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [tutorialGroupOne, tutorialGroupTwo],
                            status: 200,
                        }),
                    ),
                );
                configurationService = TestBed.inject(TutorialGroupsConfigurationService);
                getOneOfCourseSpy = jest.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: configuration })));
                navigateSpy = jest.spyOn(router, 'navigate');
                navigateSpy.mockClear();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        fixture.destroy();
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
        expect(getAllOfCourseSpy).toHaveBeenCalledOnce();
        expect(getAllOfCourseSpy).toHaveBeenCalledWith(1);
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should get all tutorial groups for course', () => {
        expect(component.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(getAllOfCourseSpy).toHaveBeenCalledOnce();
        expect(getAllOfCourseSpy).toHaveBeenCalledWith(1);
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should get all tutorial groups for course if import is done', () => {
        getAllOfCourseSpy.mockClear();
        getOneOfCourseSpy.mockClear();
        expect(getOneOfCourseSpy).not.toHaveBeenCalled();
        expect(getAllOfCourseSpy).not.toHaveBeenCalled();
        const mockTutorialGroupImportButtonComponent = fixture.debugElement.query(By.directive(MockTutorialGroupsImportButtonComponent)).componentInstance;
        mockTutorialGroupImportButtonComponent.importFinished.emit();
        expect(getAllOfCourseSpy).toHaveBeenCalledOnce();
        expect(getAllOfCourseSpy).toHaveBeenCalledWith(1);
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should navigate to tutorial group detail page when tutorial group click callback is called', () => {
        component.courseId = 1;
        component.onTutorialGroupSelected(tutorialGroupOne);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups', tutorialGroupOne.id]);
    });
});
