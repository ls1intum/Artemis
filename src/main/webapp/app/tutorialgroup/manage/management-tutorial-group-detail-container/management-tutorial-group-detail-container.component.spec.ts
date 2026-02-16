import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ManagementTutorialGroupDetailContainerComponent } from 'app/tutorialgroup/manage/management-tutorial-group-detail-container/management-tutorial-group-detail-container.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';

describe('TutorialGroupManagementDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ManagementTutorialGroupDetailContainerComponent>;
    let component: ManagementTutorialGroupDetailContainerComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ManagementTutorialGroupDetailContainerComponent, OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: DialogService, useClass: MockDialogService },
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
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ManagementTutorialGroupDetailContainerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
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

        const findByIdStub = vi.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
        fixture.detectChanges();
        expect(component.tutorialGroup).toEqual(tutorialGroupOfResponse);
        expect(component.tutorialGroupId).toBe(1);
        expect(component.course.id).toBe(2);
        expect(findByIdStub).toHaveBeenCalledWith(2, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
    });
});
