import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DebugElement, EmbeddedViewRef, getDebugNode } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
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
import { TutorialGroupRowButtonsComponent } from './tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { provideArtemisTumUiTranslator } from 'app/shared-ui/tum-ui-integration/artemis-tum-ui-translator';

interface TutorialGroupApiServiceMock {
    getTutorialGroupsForCourse: ReturnType<typeof vi.fn>;
}

describe('TutorialGroupsManagementComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsManagementComponent>;
    let component: TutorialGroupsManagementComponent;
    const configuration = generateExampleTutorialGroupsConfiguration({});
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true, isAtLeastEditor: true, tutorialGroupsConfiguration: configuration } as Course;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    let tutorialGroupApiServiceMock: TutorialGroupApiServiceMock;
    let configurationService: TutorialGroupsConfigurationService;
    let getOneOfCourseSpy: ReturnType<typeof vi.spyOn>;

    const router = new MockRouter();

    // Embedded views created by renderTitleBarActions() are tracked so they can be destroyed after each test,
    // preventing leaked component instances and subscriptions across tests.
    let titleBarActionViews: EmbeddedViewRef<unknown>[] = [];

    /** Renders the `*titleBarActions` template as `jhi-course-title-bar` would, so its controls can be queried. */
    function renderTitleBarActions(): DebugElement {
        const actionsTemplate = TestBed.inject(CourseTitleBarService).actionsTemplate();
        expect(actionsTemplate).toBeDefined();
        const view = actionsTemplate!.createEmbeddedView({});
        titleBarActionViews.push(view);
        view.detectChanges();
        return getDebugNode(view.rootNodes[0]) as DebugElement;
    }

    /** Text of every rendered body cell, row by row. */
    function renderedRows(): string[][] {
        return fixture.debugElement.queryAll(By.css('tr[cdk-row]')).map((row) => row.queryAll(By.css('td[cdk-cell]')).map((cell) => (cell.nativeElement.textContent ?? '').trim()));
    }

    /** Enough groups to spill over the table's default page size of 50. */
    function manyGroups(count: number): TutorialGroup[] {
        return Array.from({ length: count }, (_, index) => generateExampleTutorialGroup({ id: index + 1, title: `Group-${String(index).padStart(2, '0')}` }));
    }

    function goToNextPage(): void {
        fixture.debugElement.query(By.css('button[aria-label="global.paginator.next"]')).nativeElement.click();
        fixture.detectChanges();
    }

    function search(term: string): void {
        const input: HTMLInputElement = renderTitleBarActions().query(By.css('[data-testid="tutorial-groups-search"] input')).nativeElement;
        input.value = term;
        input.dispatchEvent(new Event('input'));
        fixture.detectChanges();
    }

    async function setUp(tutorialGroups: TutorialGroup[]): Promise<void> {
        tutorialGroupApiServiceMock.getTutorialGroupsForCourse.mockReturnValue(of(tutorialGroups));
        fixture = TestBed.createComponent(TutorialGroupsManagementComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        // The table emits its first data request after the initial render, which is what fills the page.
        await fixture.whenStable();
        fixture.detectChanges();
    }

    beforeEach(async () => {
        tutorialGroupApiServiceMock = {
            getTutorialGroupsForCourse: vi.fn(),
        };
        await TestBed.configureTestingModule({
            imports: [TutorialGroupsManagementComponent],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                { provide: TutorialGroupApi, useValue: tutorialGroupApiServiceMock },
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
                // The paginator and the table's empty row translate through this adapter, as they do in the app.
                provideArtemisTumUiTranslator(),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        tutorialGroupOne = generateExampleTutorialGroup({ id: 1, title: 'Mon-1', teachingAssistantName: 'Ada Lovelace' });
        tutorialGroupTwo = generateExampleTutorialGroup({ id: 2, title: 'Fri-2', teachingAssistantName: 'Grace Hopper', campus: 'Garching' });

        configurationService = TestBed.inject(TutorialGroupsConfigurationService);
        getOneOfCourseSpy = vi.spyOn(configurationService, 'getOneOfCourse');
        await setUp([tutorialGroupOne, tutorialGroupTwo]);
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
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledWith(1);
        expect(component.configuration()).toEqual(configuration);
        expect(getOneOfCourseSpy).not.toHaveBeenCalled();
    });

    it('should get all tutorial groups for course', () => {
        expect(component.tutorialGroups()).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledWith(1);
        expect(getOneOfCourseSpy).not.toHaveBeenCalled();
    });

    it('should get all tutorial groups for course if import is done', () => {
        tutorialGroupApiServiceMock.getTutorialGroupsForCourse.mockClear();
        getOneOfCourseSpy.mockClear();
        const tutorialGroupImportButtonComponent = renderTitleBarActions().query(By.directive(TutorialGroupsImportButtonComponent)).componentInstance;
        tutorialGroupImportButtonComponent.importFinished.emit();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledWith(1);
        expect(getOneOfCourseSpy).not.toHaveBeenCalled();
    });

    it('should complete export when export button is clicked', () => {
        tutorialGroupApiServiceMock.getTutorialGroupsForCourse.mockClear();
        getOneOfCourseSpy.mockClear();
        const tutorialGroupExportButtonComponent = renderTitleBarActions().query(By.directive(TutorialGroupsExportButtonComponent)).componentInstance;
        tutorialGroupExportButtonComponent.exportFinished.emit();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledWith(1);
        expect(getOneOfCourseSpy).not.toHaveBeenCalled();
    });

    it('should reload the groups after a row was deleted', () => {
        tutorialGroupApiServiceMock.getTutorialGroupsForCourse.mockClear();
        const rowButtons = fixture.debugElement.query(By.directive(TutorialGroupRowButtonsComponent)).componentInstance;
        rowButtons.tutorialGroupDeleted.emit();
        expect(tutorialGroupApiServiceMock.getTutorialGroupsForCourse).toHaveBeenCalledOnce();
    });

    it('should render one row per tutorial group, sorted by title', () => {
        const rows = renderedRows();
        expect(rows).toHaveLength(2);
        // The table starts sorted by title ascending, so 'Fri-2' precedes 'Mon-1'.
        expect(rows[0][0]).toBe('Fri-2');
        expect(rows[1][0]).toBe('Mon-1');
    });

    it('should render the tutor, the registrations against the capacity, the room and the campus', () => {
        const [fridayRow] = renderedRows();
        expect(fridayRow[1]).toBe('Grace Hopper');
        expect(fridayRow[3]).toBe('5 / 10');
        expect(fridayRow[4]).toBe('Example Location');
        expect(fridayRow[5]).toBe('Garching');
    });

    // The example helper defaults the campus, so an absent one has to be cleared after construction.
    function groupWithoutCampus(isOnline: boolean): TutorialGroup {
        const group = generateExampleTutorialGroup({ id: 3, title: 'Group', isOnline });
        group.campus = undefined;
        return group;
    }

    it('should name the mode in the campus column for an online group that has no campus', async () => {
        await setUp([groupWithoutCampus(true)]);
        expect(renderedRows()[0][5]).toBe('artemisApp.generic.online');
    });

    it('should keep the campus in the campus column when an online group has one', async () => {
        await setUp([generateExampleTutorialGroup({ id: 3, title: 'Group', isOnline: true, campus: 'Garching' })]);
        expect(renderedRows()[0][5]).toBe('Garching');
    });

    it('should name the mode in the campus column for an offline group that has no campus', async () => {
        await setUp([groupWithoutCampus(false)]);
        expect(renderedRows()[0][5]).toBe('artemisApp.generic.offline');
    });

    it('should label the tutor column with "you" for the groups the current user tutors', async () => {
        await setUp([generateExampleTutorialGroup({ id: 3, title: 'Own', isUserTutor: true })]);
        expect(renderedRows()[0][1]).toBe('global.generic.you');
    });

    it('should filter the rows by the search term', () => {
        search('grace');
        const rows = renderedRows();
        expect(rows).toHaveLength(1);
        expect(rows[0][0]).toBe('Fri-2');
    });

    it('should match the search term against the room and the campus as well', () => {
        search('garching');
        expect(renderedRows()).toHaveLength(1);
        search('example location');
        expect(renderedRows()).toHaveLength(2);
    });

    it('should show the empty message when nothing matches the search term', () => {
        search('no such group');
        expect(renderedRows()).toHaveLength(0);
        expect(fixture.nativeElement.textContent).toContain('artemisApp.pages.tutorialGroupsManagement.noMatchingGroups');
    });

    it('should reverse the order when a sortable header is clicked', () => {
        const titleHeader: HTMLButtonElement = fixture.debugElement.queryAll(By.css('th[cdk-header-cell] button'))[0].nativeElement;
        titleHeader.click();
        fixture.detectChanges();
        expect(renderedRows().map((row) => row[0])).toEqual(['Mon-1', 'Fri-2']);
    });

    it('should only render the rows of the current page', async () => {
        await setUp(manyGroups(60));

        expect(renderedRows()).toHaveLength(50);
        expect(renderedRows()[0][0]).toBe('Group-00');

        goToNextPage();

        expect(renderedRows()).toHaveLength(10);
        expect(renderedRows()[0][0]).toBe('Group-50');
    });

    it('should return to the first page when the search term changes', async () => {
        await setUp(manyGroups(60));
        goToNextPage();
        expect(renderedRows()[0][0]).toBe('Group-50');

        search('group-0');

        expect(renderedRows()[0][0]).toBe('Group-00');
    });

    it('should show the intro message instead of the table when the course has no tutorial groups', async () => {
        await setUp([]);
        expect(fixture.debugElement.query(By.css('[data-testid="tutorial-groups-table"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="tutorial-groups-intro"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.directive(TutorialGroupsImportButtonComponent))).not.toBeNull();
    });
});
