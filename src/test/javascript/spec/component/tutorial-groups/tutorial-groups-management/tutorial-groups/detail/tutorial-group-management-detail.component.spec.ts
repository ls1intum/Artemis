import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupManagementDetailComponent } from 'app/tutorialgroup/manage/tutorial-groups/detail/tutorial-group-management-detail.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/services/tutorial-groups.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorialGroupManagementDetailComponent', () => {
    let fixture: ComponentFixture<TutorialGroupManagementDetailComponent>;
    let component: TutorialGroupManagementDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
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
        expect(component.course.id).toBe(2);
        expect(findByIdStub).toHaveBeenCalledWith(2, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
    });
});
