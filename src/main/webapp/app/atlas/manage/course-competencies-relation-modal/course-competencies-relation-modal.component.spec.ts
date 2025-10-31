import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetenciesRelationModalComponent } from 'app/atlas/manage/course-competencies-relation-modal/course-competencies-relation-modal.component';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbActiveModalService } from 'test/helpers/mocks/service/mock-ngb-active-modal.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CourseCompetenciesRelationModalComponent', () => {
    let component: CourseCompetenciesRelationModalComponent;
    let fixture: ComponentFixture<CourseCompetenciesRelationModalComponent>;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let alertService: AlertService;
    let activeModal: NgbActiveModal;

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
        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: NgbActiveModal,
                    useClass: MockNgbActiveModalService,
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
                        getCourseCompetencyRelationsByCourseId: jest.fn(),
                    },
                },
            ],
        }).compileComponents();

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        alertService = TestBed.inject(AlertService);
        activeModal = TestBed.inject(NgbActiveModal);

        fixture = TestBed.createComponent(CourseCompetenciesRelationModalComponent);
        component = fixture.componentInstance;

        jest.spyOn(courseCompetencyApiService, 'getCourseCompetencyRelationsByCourseId').mockResolvedValue(relations);

        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('courseCompetencies', courseCompetencies);
    });

    afterEach(() => {
        jest.clearAllMocks();
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
        const errorSpy = jest.spyOn(alertService, 'addAlert');
        jest.spyOn(courseCompetencyApiService, 'getCourseCompetencyRelationsByCourseId').mockReturnValue(Promise.reject(new Error('Error')));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should closeModal', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');

        component['closeModal']();

        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should call selectCourseCompetency on courseCompetencyRelationFormComponent with valid courseCompetencyId', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.detectChanges(); // required as the viewChild is only available after effect() has run (-> second update)
        const courseCompetencyId = 1;
        const selectSpy = jest.spyOn(component['courseCompetencyRelationFormComponent'](), 'selectCourseCompetency');

        component['selectCourseCompetency'](courseCompetencyId);

        expect(selectSpy).toHaveBeenCalledExactlyOnceWith(courseCompetencyId);
    });
});
