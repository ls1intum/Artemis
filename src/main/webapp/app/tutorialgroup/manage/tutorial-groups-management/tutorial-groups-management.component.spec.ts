import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DebugElement, EmbeddedViewRef, getDebugNode } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsManagementComponent } from 'app/tutorialgroup/manage/tutorial-groups-management/tutorial-groups-management.component';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { By } from '@angular/platform-browser';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { Course } from 'app/course/shared/entities/course.model';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupsImportButtonComponent } from './tutorial-groups-import-button/tutorial-groups-import-button.component';
import { TutorialGroupsExportButtonComponent } from './tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';
import { tutorialGroupConfigurationDtoFromEntity } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';

interface TutorialGroupApiServiceMock {
    getTutorialGroupsForCourse: ReturnType<typeof vi.fn>;
}

describe('TutorialGroupsManagementComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupsManagementComponent>;
    let component: TutorialGroupsManagementComponent;
    const configuration = generateExampleTutorialGroupsConfiguration({});
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true, isAtLeastEditor: true } as Course;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    let tutorialGroupApiServiceMock: TutorialGroupApiServiceMock;
    let configurationService: TutorialGroupsConfigurationService;
    let getOneOfCourseSpy: ReturnType<typeof vi.spyOn>;

    const router = new MockRouter();

    // Embedded views created by renderTitleBarActions() are tracked so they can be destroyed after each test,
    // preventing leaked component instances and subscriptions across tests.
    let titleBarActionViews: EmbeddedViewRef<unknown>[] = [];

    /**
     * The Holidays / Create / More actions (including the import and export buttons) are projected into the
     * shared course title bar via the `*titleBarActions` directive, so they are not part of the component's own
     * DOM. This renders the registered actions template the same way `jhi-course-title-bar` would, allowing the
     * projected buttons to be queried.
     */
    function renderTitleBarActions(): DebugElement {
        const actionsTemplate = TestBed.inject(CourseTitleBarService).actionsTemplate();
        // The directive only registers a template when the `@if (isAtLeastEditor)` block renders; assert it
        // explicitly so a missing template fails with a clear message instead of a cryptic null dereference.
        expect(actionsTemplate).toBeDefined();
        const view = actionsTemplate!.createEmbeddedView({});
        titleBarActionViews.push(view);
        view.detectChanges();
        // The `*titleBarActions` directive sits directly on the actions wrapper `<div>`, so the embedded view's
        // first root node is that element; its DebugElement.query traverses into the nested action buttons.
        return getDebugNode(view.rootNodes[0]) as DebugElement;
    }

    beforeEach(async () => {
        tutorialGroupApiServiceMock = {
            getTutorialGroupsForCourse: vi.fn(),
        };
        await TestBed.configureTestingModule({
            imports: [TutorialGroupsManagementComponent, OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiServiceMock },
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsManagementComponent);
        component = fixture.componentInstance;
        tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
        tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });

        tutorialGroupApiServiceMock.getTutorialGroupsForCourse.mockReturnValue(
            of(
                new HttpResponse({
                    body: [tutorialGroupOne, tutorialGroupTwo],
                    status: 200,
                }),
            ),
        );
        configurationService = TestBed.inject(TutorialGroupsConfigurationService);
        getOneOfCourseSpy = vi
            .spyOn(configurationService, 'getOneOfCourse')
            .mockReturnValue(of(new HttpResponse({ body: tutorialGroupConfigurationDtoFromEntity(configuration) })));
        fixture.detectChanges();
    });

    afterEach(() => {
        titleBarActionViews.forEach((view) => view.destroy());
        titleBarActionViews = [];
        fixture.destroy();
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledWith(1, 'response');
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should get all tutorial groups for course', () => {
        expect(component.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledWith(1, 'response');
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should get all tutorial groups for course if import is done', () => {
        tutorialGroupApiServiceMock.getTutorialGroupsForCourse.mockClear();
        getOneOfCourseSpy.mockClear();
        expect(getOneOfCourseSpy).not.toHaveBeenCalled();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).not.toHaveBeenCalled();
        const tutorialGroupImportButtonComponent = renderTitleBarActions().query(By.directive(TutorialGroupsImportButtonComponent)).componentInstance;
        tutorialGroupImportButtonComponent.importFinished.emit();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledWith(1, 'response');
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });
    it('should complete export when export button is clicked', () => {
        tutorialGroupApiServiceMock.getTutorialGroupsForCourse.mockClear();
        getOneOfCourseSpy.mockClear();
        expect(getOneOfCourseSpy).not.toHaveBeenCalled();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).not.toHaveBeenCalled();
        const tutorialGroupExportButtonComponent = renderTitleBarActions().query(By.directive(TutorialGroupsExportButtonComponent)).componentInstance;
        tutorialGroupExportButtonComponent.exportFinished.emit();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledWith(1, 'response');
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });
});
