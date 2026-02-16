import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsManagementComponent } from 'app/tutorialgroup/manage/tutorial-groups-management/tutorial-groups-management.component';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { By } from '@angular/platform-browser';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { TutorialGroupsImportButtonComponent } from './tutorial-groups-import-button/tutorial-groups-import-button.component';
import { TutorialGroupsExportButtonComponent } from './tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';

describe('TutorialGroupsManagementComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupsManagementComponent>;
    let component: TutorialGroupsManagementComponent;
    const configuration = generateExampleTutorialGroupsConfiguration({});
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true } as Course;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    let tutorialGroupsService: TutorialGroupsService;
    let configurationService: TutorialGroupsConfigurationService;
    let getAllOfCourseSpy: ReturnType<typeof vi.spyOn>;
    let getOneOfCourseSpy: ReturnType<typeof vi.spyOn>;

    const router = new MockRouter();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupsManagementComponent, OwlNativeDateTimeModule],
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

        tutorialGroupsService = TestBed.inject(TutorialGroupsService);
        getAllOfCourseSpy = vi.spyOn(tutorialGroupsService, 'getAllForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [tutorialGroupOne, tutorialGroupTwo],
                    status: 200,
                }),
            ),
        );
        configurationService = TestBed.inject(TutorialGroupsConfigurationService);
        getOneOfCourseSpy = vi.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: configuration })));
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
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
        const tutorialGroupImportButtonComponent = fixture.debugElement.query(By.directive(TutorialGroupsImportButtonComponent)).componentInstance;
        tutorialGroupImportButtonComponent.importFinished.emit();
        expect(getAllOfCourseSpy).toHaveBeenCalledOnce();
        expect(getAllOfCourseSpy).toHaveBeenCalledWith(1);
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });
    it('should complete export when export button is clicked', () => {
        getAllOfCourseSpy.mockClear();
        getOneOfCourseSpy.mockClear();
        expect(getOneOfCourseSpy).not.toHaveBeenCalled();
        expect(getAllOfCourseSpy).not.toHaveBeenCalled();
        const tutorialGroupExportButtonComponent = fixture.debugElement.query(By.directive(TutorialGroupsExportButtonComponent)).componentInstance;
        tutorialGroupExportButtonComponent.exportFinished.emit();
        expect(getAllOfCourseSpy).toHaveBeenCalledOnce();
        expect(getAllOfCourseSpy).toHaveBeenCalledWith(1);
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(1);
    });
});
