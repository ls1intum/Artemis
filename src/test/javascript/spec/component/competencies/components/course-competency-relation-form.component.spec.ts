import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetencyRelationFormComponent, UnionFind } from 'app/course/competencies/components/course-competency-relation-form/course-competency-relation-form.component';
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

    it('should set relationAlreadyExists correctly', () => {
        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(1);
        component.relationType.set(CompetencyRelationType.ASSUMES);

        fixture.detectChanges();

        expect(component.relationAlreadyExists()).toBeTrue();
    });

    it('should set exactRelationAlreadyExists correctly', () => {
        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(1);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        fixture.detectChanges();

        expect(component.exactRelationAlreadyExists()).toBeTrue();
    });

    it('should select relation if selectedRelationId is set', () => {
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        expect(component.headCompetencyId()).toBe(2);
        expect(component.tailCompetencyId()).toBe(1);
        expect(component.relationType()).toBe(CompetencyRelationType.EXTENDS);
    });

    it('should set headCompetencyId if it is undefined', () => {
        component.headCompetencyId.set(undefined);
        component.tailCompetencyId.set(2);

        component.selectCourseCompetency(1);

        expect(component.headCompetencyId()).toBe(1);
        expect(component.tailCompetencyId()).toBeUndefined();
    });

    it('should set tailCompetencyId if headCompetencyId is defined and tailCompetencyId is undefined', () => {
        component.headCompetencyId.set(1);
        component.tailCompetencyId.set(undefined);

        component.selectCourseCompetency(2);

        expect(component.tailCompetencyId()).toBe(2);
    });

    it('should reset headCompetencyId if both headCompetencyId and tailCompetencyId are defined', () => {
        component.headCompetencyId.set(1);
        component.tailCompetencyId.set(2);

        component.selectCourseCompetency(3);

        expect(component.headCompetencyId()).toBe(3);
        expect(component.tailCompetencyId()).toBeUndefined();
    });

    it('should create relation', async () => {
        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        await component['createRelation']();

        expect(createCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, {
            headCompetencyId: 2,
            tailCompetencyId: 3,
            relationType: CompetencyRelationType.EXTENDS,
        });
        expect(component.headCompetencyId()).toBe(2);
        expect(component.tailCompetencyId()).toBe(3);
        expect(component.relationType()).toBe(CompetencyRelationType.EXTENDS);
        expect(component.selectedRelationId()).toBe(2);
        expect(component.relations()).toEqual([...relations, newRelation]);
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
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        component.relationType.set(CompetencyRelationType.ASSUMES);

        await component['updateRelation']();

        expect(updateCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, selectedRelationId, <UpdateCourseCompetencyRelationDTO>{
            newRelationType: CompetencyRelationType.ASSUMES,
        });
        const newRelations = [...relations].map((relation) => {
            if (relation.id === selectedRelationId) {
                return { ...relation, relationType: CompetencyRelationType.ASSUMES };
            }
            return relation;
        });
        expect(component.relations()).toEqual(newRelations);
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

    it('should select head course competency', () => {
        component['selectHeadCourseCompetency'](2);

        expect(component.headCompetencyId()).toBe(2);
        expect(component.tailCompetencyId()).toBeUndefined();
        expect(component.selectedRelationId()).toBeUndefined();
    });

    it('should set tailCompetencyId and selectedRelationId when an existing relation is found', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const tailId = 1;
        component.headCompetencyId.set(2);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        component['selectTailCourseCompetency'](tailId);

        expect(component.tailCompetencyId()).toBe(1);
        expect(component.selectedRelationId()).toBe(1);
    });

    it('should set tailCompetencyId and clear selectedRelationId when no existing relation is found', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const tailId = 2;
        component.headCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        component['selectTailCourseCompetency'](tailId);

        expect(component.tailCompetencyId()).toBe(2);
        expect(component.selectedRelationId()).toBeUndefined();
    });

    it('should not allow to create circular dependencies', () => {
        component.headCompetencyId.set(1);
        component.tailCompetencyId.set(1);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        expect(component['selectableTailCourseCompetencyIds']).not.toContain(1);
        expect(component.showCircularDependencyError()).toBeTrue();
    });

    it('should delete relation', async () => {
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        await component['deleteRelation']();

        expect(deleteCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, selectedRelationId);
        expect(component.relations()).toHaveLength(relations.length - 1);
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

describe('UnionFind', () => {
    let unionFind: UnionFind;

    beforeEach(() => {
        unionFind = new UnionFind(5);
    });

    it('should initialize parent and rank arrays correctly', () => {
        expect(unionFind.parent).toEqual([0, 1, 2, 3, 4]);
        expect(unionFind.rank).toEqual([1, 1, 1, 1, 1]);
    });

    it('should find the representative of a set', () => {
        expect(unionFind.find(0)).toBe(0);
        expect(unionFind.find(1)).toBe(1);
    });

    it('should perform union by rank correctly', () => {
        unionFind.union(0, 1);
        expect(unionFind.find(0)).toBe(unionFind.find(1));
    });

    it('should perform path compression correctly', () => {
        unionFind.union(0, 1);
        unionFind.union(1, 2);
        expect(unionFind.find(2)).toBe(0);
        expect(unionFind.parent[2]).toBe(0);
    });

    it('should handle union of already connected components', () => {
        unionFind.union(0, 1);
        unionFind.union(1, 2);
        unionFind.union(0, 2);
        expect(unionFind.find(2)).toBe(0);
    });

    it('should handle union of components with equal rank', () => {
        unionFind.union(0, 1);
        unionFind.union(2, 3);
        unionFind.union(1, 2);
        expect(unionFind.find(3)).toBe(0);
        expect(unionFind.rank[0]).toBe(3); // Corrected expected rank value
    });
});
