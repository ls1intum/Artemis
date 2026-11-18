import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetencyGraphNode, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CourseCompetencyRelationNodeComponent } from 'app/atlas/manage/course-competency-relation-node/course-competency-relation-node.component';

describe('CourseCompetencyRelationNodeComponent', () => {
    let component: CourseCompetencyRelationNodeComponent;
    let fixture: ComponentFixture<CourseCompetencyRelationNodeComponent>;

    const node: CourseCompetencyGraphNode = {
        id: '1',
        label: 'Competency 1',
        data: {
            id: 1,
            type: CourseCompetencyType.COMPETENCY,
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetencyRelationNodeComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseCompetencyRelationNodeComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseCompetencyNode', node);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and emit size update', async () => {
        const sizeUpdateEmitSpy = vi.spyOn(component.onSizeSet, 'emit');

        fixture.detectChanges();

        expect(component).toBeTruthy();
        expect(component.courseCompetencyNode()).toEqual(node);
        expect(sizeUpdateEmitSpy).toHaveBeenCalledOnce();
    });
});
