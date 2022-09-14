import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { LoadingIndicatorContainerStubComponent } from '../../helpers/stubs/loading-indicator-container-stub.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortService } from 'app/shared/service/sort.service';

@Component({ selector: 'jhi-tutorial-group-row-buttons', template: '' })
class TutorialGroupRowButtonsStubComponent {
    @Input() tutorialGroup: TutorialGroup;
    @Input() courseId: number;
}
describe('TutorialGroupsManagementComponent', () => {
    let tutorialGroupsManagementComponentFixture: ComponentFixture<TutorialGroupsManagementComponent>;
    let tutorialGroupsManagementComponent: TutorialGroupsManagementComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupsManagementComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupRowButtonsStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
            ],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                MockProvider(SortService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            paramMap: of({
                                get: (key: string) => {
                                    switch (key) {
                                        case 'courseId':
                                            return 1;
                                    }
                                },
                            }),
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                tutorialGroupsManagementComponentFixture = TestBed.createComponent(TutorialGroupsManagementComponent);
                tutorialGroupsManagementComponent = tutorialGroupsManagementComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        tutorialGroupsManagementComponentFixture.detectChanges();
        expect(tutorialGroupsManagementComponent).not.toBeNull();
    });

    it('should get all tutorial groups for course', () => {
        const exampleTutorialGroup = new TutorialGroup();
        exampleTutorialGroup.id = 1;

        const tutorialGroupsService = TestBed.inject(TutorialGroupsService);
        const getAllForCourseSpy = jest.spyOn(tutorialGroupsService, 'getAllOfCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [exampleTutorialGroup],
                    status: 200,
                }),
            ),
        );

        tutorialGroupsManagementComponentFixture.detectChanges();
        expect(tutorialGroupsManagementComponent.tutorialGroups).toEqual([exampleTutorialGroup]);
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(getAllForCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should call sort service', () => {
        const group1 = new TutorialGroup();
        group1.id = 1;
        const group2 = new TutorialGroup();
        group2.id = 2;

        tutorialGroupsManagementComponent.tutorialGroups = [group1, group2];
        tutorialGroupsManagementComponent.sortingPredicate = 'id';
        tutorialGroupsManagementComponent.ascending = false;

        const sortService = TestBed.inject(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        tutorialGroupsManagementComponent.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([group1, group2], 'id', false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });
});
