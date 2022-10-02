import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupDetailComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/detail/tutorial-group-detail.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { of } from 'rxjs';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroupRowButtonsStubComponent } from '../../../stubs/tutorial-group-row-buttons-stub.component';
import { Course } from 'app/entities/course.model';

describe('TutorialGroupDetailComponent', () => {
    let tutorialGroupDetailComponentFixture: ComponentFixture<TutorialGroupDetailComponent>;
    let tutorialGroupDetailComponent: TutorialGroupDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupDetailComponent,
                TutorialGroupRowButtonsStubComponent,
                LoadingIndicatorContainerStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                MockProvider(ArtemisMarkdownService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'tutorialGroupId':
                                        return 1;
                                }
                            },
                        }),
                        parent: {
                            paramMap: of({
                                get: (key: string) => {
                                    switch (key) {
                                        case 'courseId':
                                            return 2;
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
                tutorialGroupDetailComponentFixture = TestBed.createComponent(TutorialGroupDetailComponent);
                tutorialGroupDetailComponent = tutorialGroupDetailComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        tutorialGroupDetailComponentFixture.detectChanges();
        expect(tutorialGroupDetailComponent).not.toBeNull();
    });

    it('should load tutorial group', () => {
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);

        const tutorialGroupOfResponse = new TutorialGroup();
        tutorialGroupOfResponse.id = 1;
        tutorialGroupOfResponse.title = 'test';
        tutorialGroupOfResponse.course = new Course();
        tutorialGroupOfResponse.course.id = 2;

        const response: HttpResponse<TutorialGroup> = new HttpResponse({
            body: tutorialGroupOfResponse,
            status: 200,
        });

        const findByIdStub = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
        tutorialGroupDetailComponentFixture.detectChanges();
        expect(tutorialGroupDetailComponent.tutorialGroup).toEqual(tutorialGroupOfResponse);
        expect(tutorialGroupDetailComponent.tutorialGroupId).toBe(1);
        expect(tutorialGroupDetailComponent.courseId).toBe(2);
        expect(findByIdStub).toHaveBeenCalledWith(2, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
    });
});
