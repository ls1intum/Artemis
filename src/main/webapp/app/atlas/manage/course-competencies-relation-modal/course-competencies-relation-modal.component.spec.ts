import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetenciesRelationModalComponent } from 'app/atlas/manage/course-competencies-relation-modal/course-competencies-relation-modal.component';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';
import { CourseCompetenciesRelationGraphComponent } from 'app/atlas/manage/course-competencies-relation-graph/course-competencies-relation-graph.component';
import { MockComponent } from 'ng-mocks';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('CourseCompetenciesRelationModalComponent', () => {
    setupTestBed({ zoneless: true });
    let component: CourseCompetenciesRelationModalComponent;
    let fixture: ComponentFixture<CourseCompetenciesRelationModalComponent>;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let alertService: AlertService;
    let dialogRef: DynamicDialogRef;

    const courseId = 1;
    const courseCompetencies: CourseCompetency[] = [
        { id: 1, type: CourseCompetencyType.COMPETENCY },
        { id: 2, type: CourseCompetencyType.PREREQUISITE },
    ];
    const relations: CompetencyRelationDTO[] = [
        {
            id: 1,
            relationType: CompetencyRelationType.EXTENDS,
            tailCompetencyId: 1,
            headCompetencyId: 2,
        },
    ];

    beforeEach(async () => {
        dialogRef = {
            close: vi.fn(),
            onClose: new Subject<unknown>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: DynamicDialogRef,
                    useValue: dialogRef,
                },
                {
                    provide: DynamicDialogConfig,
                    useValue: { data: { courseId, courseCompetencies } },
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: CourseCompetencyApiService,
                    useValue: {
                        getCourseCompetencyRelationsByCourseId: vi.fn(),
                    },
                },
                provideNoopAnimationsForTests(),
            ],
        })
            .overrideComponent(CourseCompetenciesRelationModalComponent, {
                remove: { imports: [CourseCompetenciesRelationGraphComponent] },
                add: { imports: [MockComponent(CourseCompetenciesRelationGraphComponent)] },
            })
            .compileComponents();

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        alertService = TestBed.inject(AlertService);
        dialogRef = TestBed.inject(DynamicDialogRef);

        fixture = TestBed.createComponent(CourseCompetenciesRelationModalComponent);
        component = fixture.componentInstance;

        vi.spyOn(courseCompetencyApiService, 'getCourseCompetencyRelationsByCourseId').mockResolvedValue(relations);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should load relations', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.relations()).toEqual(relations);
    });

    it('should show alert on error', async () => {
        const errorSpy = vi.spyOn(alertService, 'addAlert');
        vi.spyOn(courseCompetencyApiService, 'getCourseCompetencyRelationsByCourseId').mockReturnValue(Promise.reject(new Error('Error')));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = vi.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should closeModal', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component['closeModal']();

        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should call selectCourseCompetency on courseCompetencyRelationFormComponent with valid courseCompetencyId', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.detectChanges(); // required as the viewChild is only available after effect() has run (-> second update)
        const courseCompetencyId = 1;
        const selectSpy = vi.spyOn(component['courseCompetencyRelationFormComponent'](), 'selectCourseCompetency');

        component['selectCourseCompetency'](courseCompetencyId);

        expect(selectSpy).toHaveBeenCalledExactlyOnceWith(courseCompetencyId);
    });
});
