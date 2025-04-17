import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsManagementComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { By } from '@angular/platform-browser';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { NgbDropdownModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsImportButtonComponent } from './tutorial-groups-import-button/tutorial-groups-import-button.component';
import { TutorialGroupsExportButtonComponent } from './tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
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

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbDropdownModule, MockDirective(NgbTooltip)],
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
            ],
        }).compileComponents();

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
        fixture.detectChanges();
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
