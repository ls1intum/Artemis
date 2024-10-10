import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetencyRelationFormComponent } from 'app/course/competencies/components/course-competency-relation-form/course-competency-relation-form.component';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, UpdateCourseCompetencyRelationDTO } from 'app/entities/competency.model';

describe('CourseCompetencyRelationFormComponent', () => {
    let component: CourseCompetencyRelationFormComponent;
    let fixture: ComponentFixture<CourseCompetencyRelationFormComponent>;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let alertService: AlertService;

    let createCourseCompetencyRelationSpy: jest.SpyInstance;
    let updateCourseCompetencyRelationSpy: jest.SpyInstance;
    let deleteCourseCompetencyRelationSpy: jest.SpyInstance;

    const courseId = 1;
    const courseCompetencies: CourseCompetency[] = [
        { id: 1, title: 'Competency 1' },
        { id: 2, title: 'Competency 2' },
        { id: 3, title: 'Competency 3' },
    ];
    const relations: CompetencyRelationDTO[] = [
        {
            id: 1,
            tailCompetencyId: 1,
            headCompetencyId: 2,
            relationType: CompetencyRelationType.EXTENDS,
        },
    ];
    const selectedRelationId = 1;

    const newRelation = <CompetencyRelationDTO>{
        id: 2,
        headCompetencyId: 2,
        tailCompetencyId: 3,
        relationType: CompetencyRelationType.EXTENDS,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetencyRelationFormComponent],
            providers: [
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: CourseCompetencyApiService,
                    useValue: {
                        createCourseCompetencyRelation: jest.fn(),
                        updateCourseCompetencyRelation: jest.fn(),
                        deleteCourseCompetencyRelation: jest.fn(),
                    },
                },
            ],
        }).compileComponents();

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        alertService = TestBed.inject(AlertService);

        createCourseCompetencyRelationSpy = jest.spyOn(courseCompetencyApiService, 'createCourseCompetencyRelation').mockResolvedValue(newRelation);
        updateCourseCompetencyRelationSpy = jest.spyOn(courseCompetencyApiService, 'updateCourseCompetencyRelation').mockResolvedValue();
        deleteCourseCompetencyRelationSpy = jest.spyOn(courseCompetencyApiService, 'deleteCourseCompetencyRelation').mockResolvedValue();

        fixture = TestBed.createComponent(CourseCompetencyRelationFormComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('courseCompetencies', courseCompetencies);
        fixture.componentRef.setInput('relations', relations);
        fixture.componentRef.setInput('selectedRelationId', undefined);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeDefined();
    });

    it('should set relationAlreadyExists correctly', () => {
        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(1);
        component.relationType.set(CompetencyRelationType.ASSUMES);

        fixture.detectChanges();

        expect(component['relationAlreadyExists']()).toBeTrue();
    });

    it('should set relationTypeAlreadyExists correctly', () => {
        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(1);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        fixture.detectChanges();

        expect(component.relationTypeAlreadyExists()).toBeTrue();
    });

    it('should select relation if selectedRelationId is set', () => {
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        expect(component.headCompetencyId()).toBe(2);
        expect(component.tailCompetencyId()).toBe(1);
        expect(component.relationType()).toBe(CompetencyRelationType.EXTENDS);
    });

    it('should set possible tail competencies correctly if nothing is selected', () => {
        expect(component.selectableTailCourseCompetencies()).toEqual(courseCompetencies);
    });

    it('should set head competency on selection if undefined', () => {
        component.selectCourseCompetency(1);

        expect(component.headCompetencyId()).toBe(1);
    });

    it('should reset form', () => {
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        component['resetForm']();

        expect(component.headCompetencyId()).toBeUndefined();
        expect(component.tailCompetencyId()).toBeUndefined();
        expect(component.relationType()).toBeUndefined();
    });

    it('should create relation', async () => {
        const newRelation = <CompetencyRelationDTO>{
            id: 2,
            headCompetencyId: 2,
            tailCompetencyId: 3,
            relationType: CompetencyRelationType.EXTENDS,
        };
        const onRelationCreationSpy = jest.spyOn(component.onRelationCreation, 'emit');

        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        await component['createRelation']();

        expect(createCourseCompetencyRelationSpy).toHaveBeenCalledWith(courseId, {
            headCompetencyId: 2,
            tailCompetencyId: 3,
            relationType: CompetencyRelationType.EXTENDS,
        });
        expect(onRelationCreationSpy).toHaveBeenCalledExactlyOnceWith(newRelation);
        expect(component.headCompetencyId()).toBeUndefined();
        expect(component.tailCompetencyId()).toBeUndefined();
        expect(component.relationType()).toBeUndefined();
    });

    it('should set isLoading correctly when creating a relation', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        await component['createRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when creating relation fails', async () => {
        const error = 'Error creating relation';
        createCourseCompetencyRelationSpy.mockRejectedValue(error);
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        await component['createRelation']();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should update relation', async () => {
        const onRelationUpdateSpy = jest.spyOn(component.onRelationUpdate, 'emit');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        component.relationType.set(CompetencyRelationType.ASSUMES);

        await component['updateRelation']();

        expect(updateCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, selectedRelationId, <UpdateCourseCompetencyRelationDTO>{
            newRelationType: CompetencyRelationType.ASSUMES,
        });
        expect(onRelationUpdateSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly when updating a relation', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        component.relationType.set(CompetencyRelationType.ASSUMES);

        await component['updateRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when updating relation fails', async () => {
        updateCourseCompetencyRelationSpy.mockRejectedValue('Error updating relation');
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        component.relationType.set(CompetencyRelationType.ASSUMES);

        await component['updateRelation']();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should delete relation', async () => {
        const onRelationDeleteSpy = jest.spyOn(component.onRelationDeletion, 'emit');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        await component['deleteRelation']();

        expect(deleteCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, selectedRelationId);
        expect(onRelationDeleteSpy).toHaveBeenCalledOnce();
        expect(component.headCompetencyId()).toBeUndefined();
        expect(component.tailCompetencyId()).toBeUndefined();
        expect(component.relationType()).toBeUndefined();
    });

    it('should set isLoading correctly when deleting a relation', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        await component['deleteRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when deleting relation fails', async () => {
        deleteCourseCompetencyRelationSpy.mockRejectedValue('Error deleting relation');
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        await component['deleteRelation']();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });
});
