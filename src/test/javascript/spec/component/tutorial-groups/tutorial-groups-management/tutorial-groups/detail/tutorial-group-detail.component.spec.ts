import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupDetailComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/detail/tutorial-group-detail.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { of } from 'rxjs';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroupRowButtonsStubComponent } from '../../../stubs/tutorial-group-row-buttons-stub.component';
import { simpleOneLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';

describe('TutorialGroupDetailComponent', () => {
    let fixture: ComponentFixture<TutorialGroupDetailComponent>;
    let component: TutorialGroupDetailComponent;

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
                simpleOneLayerActivatedRouteProvider(
                    new Map([
                        ['courseId', 2],
                        ['tutorialGroupId', 1],
                    ]),
                ),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupDetailComponent);
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

        const tutorialGroupOfResponse = generateExampleTutorialGroup();

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
});
