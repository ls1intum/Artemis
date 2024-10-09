import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetenciesRelationModalComponent } from 'app/course/competencies/components/course-competencies-relation-modal/course-competencies-relation-modal.component';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbActiveModalService } from '../../../helpers/mocks/service/mock-ngb-active-modal.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

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
            imports: [CourseCompetenciesRelationModalComponent, NoopAnimationsModule],
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

        expect(closeSpy).toHaveBeenCalled();
    });

    it('should call selectCourseCompetency on courseCompetencyRelationFormComponent with valid courseCompetencyId', () => {
        fixture.detectChanges();

        const courseCompetencyId = 1;
        const selectSpy = jest.spyOn(component['courseCompetencyRelationFormComponent'](), 'selectCourseCompetency');

        component['selectCourseCompetency'](courseCompetencyId);

        expect(selectSpy).toHaveBeenCalledWith(courseCompetencyId);
    });

    it('should add new relation', async () => {
        const newRelation: CompetencyRelationDTO = {
            id: 2,
            relationType: CompetencyRelationType.EXTENDS,
            tailCompetencyId: 2,
            headCompetencyId: 1,
        };

        fixture.detectChanges();
        await fixture.whenStable();

        component['addRelation'](newRelation);

        expect(component.relations()).toEqual([...relations, newRelation]);
    });

    it('should update relation', async () => {
        const updatedRelation: CompetencyRelationDTO = {
            id: 1,
            relationType: CompetencyRelationType.EXTENDS,
            tailCompetencyId: 2,
            headCompetencyId: 1,
        };

        fixture.detectChanges();
        await fixture.whenStable();

        component['updateRelation'](updatedRelation);

        expect(component.relations()).toContain(updatedRelation);
    });

    it('should delete relation', async () => {
        const deletedRelationId = 1;

        fixture.detectChanges();
        await fixture.whenStable();

        component['deleteRelation'](deletedRelationId);

        expect(component.relations()).not.toContainEqual(expect.objectContaining({ id: deletedRelationId }));
    });

    it('should deselect relation', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        component['deselectRelation']();

        expect(component.selectedRelationId()).toBeUndefined();
    });
});
