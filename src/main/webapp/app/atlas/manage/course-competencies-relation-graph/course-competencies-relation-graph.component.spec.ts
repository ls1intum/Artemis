import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetenciesRelationGraphComponent } from 'app/atlas/manage/course-competencies-relation-graph/course-competencies-relation-graph.component';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseCompetenciesRelationGraphComponent', () => {
    let component: CourseCompetenciesRelationGraphComponent;
    let fixture: ComponentFixture<CourseCompetenciesRelationGraphComponent>;

    const courseCompetencies: CourseCompetency[] = [
        <CourseCompetency>{
            id: 1,
            type: CourseCompetencyType.COMPETENCY,
            title: 'Competency',
        },
        <CourseCompetency>{
            id: 2,
            type: CourseCompetencyType.PREREQUISITE,
            title: 'Prerequisite',
        },
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
            imports: [CourseCompetenciesRelationGraphComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseCompetenciesRelationGraphComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseCompetencies', courseCompetencies);
        fixture.componentRef.setInput('relations', relations);
        fixture.componentRef.setInput('selectedRelationId', 1);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', async () => {
        expect(component).toBeDefined();
        expect(component.courseCompetencies()).toEqual(courseCompetencies);
        expect(component.relations()).toEqual(relations);
    });

    it('should map edges correctly', () => {
        expect(component.edges()).toEqual(
            relations.map((relation) => {
                return {
                    id: 'edge-' + relation.id!.toString(),
                    source: relation.headCompetencyId!.toString(),
                    target: relation.tailCompetencyId!.toString(),
                    label: relation.relationType,
                    data: {
                        id: relation.id,
                    },
                };
            }),
        );
    });

    it('should map nodes correctly', () => {
        fixture.detectChanges();

        expect(component.nodes()).toMatchObject(
            courseCompetencies.map((cc) => {
                return {
                    id: cc.id!.toString(),
                    label: cc.title,
                    data: {
                        id: cc.id,
                        type: cc.type,
                    },
                };
            }),
        );
        // the rendered node components measure themselves and report their size back
        expect(component.nodes().every((node) => node.dimension !== undefined)).toBeTruthy();
    });

    it('should update node dimension', () => {
        fixture.detectChanges();
        component['setNodeDimension']({ id: '1', dimension: { width: 0, height: 45.59 } });
        expect(component.nodes().find((node) => node.id === '1')?.dimension).toEqual({ width: 0, height: 45.59 });
    });
});
