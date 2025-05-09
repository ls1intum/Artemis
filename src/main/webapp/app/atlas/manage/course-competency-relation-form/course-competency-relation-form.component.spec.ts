import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetencyRelationFormComponent, UnionFind } from 'app/atlas/manage/course-competency-relation-form/course-competency-relation-form.component';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, UpdateCourseCompetencyRelationDTO } from 'app/atlas/shared/entities/competency.model';
import { Component } from '@angular/core';
import { getComponentInstanceFromFixture } from 'test/helpers/utils/general-test.utils';

@Component({
    template: `
        <jhi-course-competency-relation-form
            [(selectedRelationId)]="selectedRelationId"
            [courseId]="courseId"
            [(relations)]="relations"
            [courseCompetencies]="courseCompetencies"
        />
    `,
    imports: [CourseCompetencyRelationFormComponent],
})
class WrapperComponent {
    selectedRelationId?: number;
    courseId: number;
    relations: CompetencyRelationDTO[];
    courseCompetencies: CourseCompetency[];
}

describe('CourseCompetencyRelationFormComponent', () => {
    let component: WrapperComponent;
    let fixture: ComponentFixture<WrapperComponent>;
    let courseCompetencyRelationFormComponent: CourseCompetencyRelationFormComponent;
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

        fixture = TestBed.createComponent(WrapperComponent);
        component = fixture.componentInstance;
        courseCompetencyRelationFormComponent = getComponentInstanceFromFixture(fixture, 'jhi-course-competency-relation-form') as CourseCompetencyRelationFormComponent;

        component.courseId = courseId;
        component.courseCompetencies = courseCompetencies;
        component.relations = relations;
        component.selectedRelationId = undefined;

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should set relationAlreadyExists correctly', () => {
        courseCompetencyRelationFormComponent.headCompetencyId.set(2);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(1);
        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.ASSUMES);

        fixture.detectChanges();

        expect(courseCompetencyRelationFormComponent.relationAlreadyExists()).toBeTrue();
    });

    it('should set exactRelationAlreadyExists correctly', () => {
        courseCompetencyRelationFormComponent.headCompetencyId.set(2);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(1);
        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.EXTENDS);

        fixture.detectChanges();

        expect(courseCompetencyRelationFormComponent.exactRelationAlreadyExists()).toBeTrue();
    });

    it('should select relation if selectedRelationId is set', () => {
        component.selectedRelationId = selectedRelationId;

        fixture.detectChanges();

        expect(courseCompetencyRelationFormComponent.headCompetencyId()).toBe(2);
        expect(courseCompetencyRelationFormComponent.tailCompetencyId()).toBe(1);
        expect(courseCompetencyRelationFormComponent.relationType()).toBe(CompetencyRelationType.EXTENDS);
    });

    it('should set headCompetencyId if it is undefined', () => {
        courseCompetencyRelationFormComponent.headCompetencyId.set(undefined);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(2);

        courseCompetencyRelationFormComponent.selectCourseCompetency(1);

        expect(courseCompetencyRelationFormComponent.headCompetencyId()).toBe(1);
        expect(courseCompetencyRelationFormComponent.tailCompetencyId()).toBeUndefined();
    });

    it('should set tailCompetencyId if headCompetencyId is defined and tailCompetencyId is undefined', () => {
        courseCompetencyRelationFormComponent.headCompetencyId.set(1);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(undefined);

        courseCompetencyRelationFormComponent.selectCourseCompetency(2);

        expect(courseCompetencyRelationFormComponent.tailCompetencyId()).toBe(2);
    });

    it('should reset headCompetencyId if both headCompetencyId and tailCompetencyId are defined', () => {
        courseCompetencyRelationFormComponent.headCompetencyId.set(1);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(2);

        courseCompetencyRelationFormComponent.selectCourseCompetency(3);

        expect(courseCompetencyRelationFormComponent.headCompetencyId()).toBe(3);
        expect(courseCompetencyRelationFormComponent.tailCompetencyId()).toBeUndefined();
    });

    it('should create relation', async () => {
        courseCompetencyRelationFormComponent.headCompetencyId.set(2);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(3);
        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.EXTENDS);

        await courseCompetencyRelationFormComponent['createRelation']();

        expect(createCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, {
            headCompetencyId: 2,
            tailCompetencyId: 3,
            relationType: CompetencyRelationType.EXTENDS,
        });
        expect(courseCompetencyRelationFormComponent.headCompetencyId()).toBe(2);
        expect(courseCompetencyRelationFormComponent.tailCompetencyId()).toBe(3);
        expect(courseCompetencyRelationFormComponent.relationType()).toBe(CompetencyRelationType.EXTENDS);
        expect(courseCompetencyRelationFormComponent.selectedRelationId()).toBe(2);
        expect(courseCompetencyRelationFormComponent.relations()).toEqual([...relations, newRelation]);
    });

    it('should set isLoading correctly when creating a relation', async () => {
        const isLoadingSpy = jest.spyOn(courseCompetencyRelationFormComponent.isLoading, 'set');

        courseCompetencyRelationFormComponent.headCompetencyId.set(2);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(3);
        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.EXTENDS);

        await courseCompetencyRelationFormComponent['createRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when creating relation fails', async () => {
        const error = 'Error creating relation';
        createCourseCompetencyRelationSpy.mockRejectedValue(error);
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        courseCompetencyRelationFormComponent.headCompetencyId.set(2);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(3);
        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.EXTENDS);

        await courseCompetencyRelationFormComponent['createRelation']();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should update relation', async () => {
        component.selectedRelationId = selectedRelationId;

        fixture.detectChanges();

        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.ASSUMES);

        await courseCompetencyRelationFormComponent['updateRelation']();

        expect(updateCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, selectedRelationId, <UpdateCourseCompetencyRelationDTO>{
            newRelationType: CompetencyRelationType.ASSUMES,
        });
        const newRelations = [...relations].map((relation) => {
            if (relation.id === selectedRelationId) {
                return { ...relation, relationType: CompetencyRelationType.ASSUMES };
            }
            return relation;
        });
        expect(courseCompetencyRelationFormComponent.relations()).toEqual(newRelations);
    });

    it('should set isLoading correctly when updating a relation', async () => {
        const isLoadingSpy = jest.spyOn(courseCompetencyRelationFormComponent.isLoading, 'set');
        component.selectedRelationId = selectedRelationId;

        fixture.detectChanges();

        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.ASSUMES);

        await courseCompetencyRelationFormComponent['updateRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when updating relation fails', async () => {
        updateCourseCompetencyRelationSpy.mockRejectedValue('Error updating relation');
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        component.selectedRelationId = selectedRelationId;

        fixture.detectChanges();

        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.ASSUMES);

        await courseCompetencyRelationFormComponent['updateRelation']();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should select head course competency', () => {
        courseCompetencyRelationFormComponent['selectHeadCourseCompetency'](2);

        expect(courseCompetencyRelationFormComponent.headCompetencyId()).toBe(2);
        expect(courseCompetencyRelationFormComponent.tailCompetencyId()).toBeUndefined();
        expect(courseCompetencyRelationFormComponent.selectedRelationId()).toBeUndefined();
    });

    it('should set tailCompetencyId and selectedRelationId when an existing relation is found', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const tailId = 1;
        courseCompetencyRelationFormComponent.headCompetencyId.set(2);
        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.EXTENDS);

        courseCompetencyRelationFormComponent['selectTailCourseCompetency'](tailId);

        expect(courseCompetencyRelationFormComponent.tailCompetencyId()).toBe(1);
        expect(courseCompetencyRelationFormComponent.selectedRelationId()).toBe(1);
    });

    it('should set tailCompetencyId and clear selectedRelationId when no existing relation is found', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const tailId = 2;
        courseCompetencyRelationFormComponent.headCompetencyId.set(3);
        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.EXTENDS);

        courseCompetencyRelationFormComponent['selectTailCourseCompetency'](tailId);

        expect(courseCompetencyRelationFormComponent.tailCompetencyId()).toBe(2);
        expect(courseCompetencyRelationFormComponent.selectedRelationId()).toBeUndefined();
    });

    it('should not allow to create circular dependencies', () => {
        courseCompetencyRelationFormComponent.headCompetencyId.set(1);
        courseCompetencyRelationFormComponent.tailCompetencyId.set(1);
        courseCompetencyRelationFormComponent.relationType.set(CompetencyRelationType.EXTENDS);

        expect(courseCompetencyRelationFormComponent['selectableTailCourseCompetencyIds']).not.toContain(1);
        expect(courseCompetencyRelationFormComponent.showCircularDependencyError()).toBeTrue();
    });

    it('should delete relation', async () => {
        component.selectedRelationId = selectedRelationId;

        fixture.detectChanges();

        await courseCompetencyRelationFormComponent['deleteRelation']();

        expect(deleteCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, selectedRelationId);
        expect(courseCompetencyRelationFormComponent.relations()).toHaveLength(relations.length - 1);
    });

    it('should set isLoading correctly when deleting a relation', async () => {
        const isLoadingSpy = jest.spyOn(courseCompetencyRelationFormComponent.isLoading, 'set');
        component.selectedRelationId = selectedRelationId;

        fixture.detectChanges();

        await courseCompetencyRelationFormComponent['deleteRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when deleting relation fails', async () => {
        deleteCourseCompetencyRelationSpy.mockRejectedValue('Error deleting relation');
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        component.selectedRelationId = selectedRelationId;

        fixture.detectChanges();

        await courseCompetencyRelationFormComponent['deleteRelation']();

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
