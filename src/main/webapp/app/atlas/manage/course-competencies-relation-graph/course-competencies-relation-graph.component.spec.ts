import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CourseCompetenciesRelationGraphComponent } from 'app/atlas/manage/course-competencies-relation-graph/course-competencies-relation-graph.component';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Component } from '@angular/core';
import { getComponentInstanceFromFixture } from 'test/helpers/utils/general-test.utils';

@Component({
    template: `
        <jhi-course-competencies-relation-graph
            [(selectedRelationId)]="selectedRelationId"
            [relations]="relations"
            [courseCompetencies]="courseCompetencies"
            (onCourseCompetencySelection)="onCourseCompetencySelection($event)"
        />
    `,
    imports: [CourseCompetenciesRelationGraphComponent],
})
class WrapperComponent {
    selectedRelationId?: number;
    relations: CompetencyRelationDTO[];
    courseCompetencies: CourseCompetency[];

    onCourseCompetencySelection(id: number) {}
}

interface CourseCompetencyStyle {
    dimension: {
        height: number;
        width: number;
    };
    meta: {
        forceDimensions: boolean;
    };
    position: {
        x: number;
        y: number;
    };
}
type StyledCourseCompetency = CourseCompetency & CourseCompetencyStyle;

describe('CourseCompetenciesRelationGraphComponent', () => {
    let component: WrapperComponent;
    let fixture: ComponentFixture<WrapperComponent>;
    let courseCompetenciesRelationGraphComponent: CourseCompetenciesRelationGraphComponent;

    const courseCompetencies: StyledCourseCompetency[] = [
        {
            id: 1,
            type: CourseCompetencyType.COMPETENCY,
            title: 'Competency',
            dimension: { height: 45.59, width: 0 },
            meta: { forceDimensions: false },
            position: { x: 0, y: 0 },
        },
        {
            id: 2,
            type: CourseCompetencyType.PREREQUISITE,
            title: 'Prerequisite',
            dimension: { height: 45.59, width: 0 },
            meta: { forceDimensions: false },
            position: { x: 0, y: 0 },
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
            imports: [WrapperComponent, NoopAnimationsModule],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(WrapperComponent);
        component = fixture.componentInstance;
        courseCompetenciesRelationGraphComponent = getComponentInstanceFromFixture(fixture, CourseCompetenciesRelationGraphComponent);

        component.courseCompetencies = courseCompetencies;
        component.relations = relations;
        component.selectedRelationId = 1;

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', async () => {
        expect(component).toBeDefined();
        expect(courseCompetenciesRelationGraphComponent.courseCompetencies()).toEqual(courseCompetencies);
        expect(courseCompetenciesRelationGraphComponent.relations()).toEqual(relations);
    });

    it('should map edges correctly', () => {
        expect(courseCompetenciesRelationGraphComponent.edges()).toEqual(
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

        expect(courseCompetenciesRelationGraphComponent.nodes()).toEqual(
            courseCompetencies.map((cc) => {
                return {
                    id: cc.id!.toString(),
                    label: cc.title,
                    data: {
                        id: cc.id,
                        type: cc.type,
                    },
                    dimension: cc.dimension,
                    meta: cc.meta,
                    position: cc.position,
                };
            }),
        );
    });

    it('should update node dimension', () => {
        fixture.detectChanges();
        courseCompetenciesRelationGraphComponent['setNodeDimension']({ id: '1', dimension: { width: 0, height: 45.59 } });
        expect(courseCompetenciesRelationGraphComponent.nodes().find((node) => node.id === '1')?.dimension).toEqual({ width: 0, height: 45.59 });
    });
});
