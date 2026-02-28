import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupsChecklistComponent } from 'app/tutorialgroup/manage/tutorial-groups-checklist/tutorial-groups-checklist.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Router } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { generateExampleTutorialGroupsConfigurationDTO } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorialGroupsChecklistComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupsChecklistComponent>;
    let component: TutorialGroupsChecklistComponent;
    let courseManagementService: CourseManagementService;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    const course = { id: 1, title: 'Example' };
    const router = new MockRouter();
    let getCourseSpy: ReturnType<typeof vi.spyOn>;
    let getOneOfCourseSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TutorialGroupsChecklistComponent, FaIconComponent],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
                mockedActivatedRoute({ courseId: course.id! }, {}, {}, {}),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsChecklistComponent);
                component = fixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                getCourseSpy = vi.spyOn(courseManagementService, 'find').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: course,
                            status: 200,
                        }),
                    ),
                );
                tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService);
                getOneOfCourseSpy = vi.spyOn(tutorialGroupsConfigurationService, 'getOneOfCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: generateExampleTutorialGroupsConfigurationDTO({}),
                            status: 200,
                        }),
                    ),
                );
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(getCourseSpy).toHaveBeenCalledWith(course.id!);
        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(getOneOfCourseSpy).toHaveBeenCalledWith(course.id!);
        expect(getOneOfCourseSpy).toHaveBeenCalledOnce();
        expect(component.isFullyConfigured).toBe(false);
    });
});
