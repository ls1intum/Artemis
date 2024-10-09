import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseCompetencyRelationNodeComponent } from 'app/course/competencies/components/course-competencies-relation-node/course-competency-relation-node.component';
import { CourseCompetencyType } from 'app/entities/competency.model';
import { Node } from '@swimlane/ngx-graph';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseCompetencyRelationNodeComponent', () => {
    let component: CourseCompetencyRelationNodeComponent;
    let fixture: ComponentFixture<CourseCompetencyRelationNodeComponent>;

    const node: Node = {
        id: '1',
        label: 'Competency 1',
        data: {
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
        jest.restoreAllMocks();
    });

    it('should initialize and emit size update', async () => {
        const sizeUpdateEmitSpy = jest.spyOn(component.onSizeSet, 'emit');

        fixture.detectChanges();

        expect(component).toBeTruthy();
        expect(component.courseCompetencyNode()).toEqual(node);
        expect(sizeUpdateEmitSpy).toHaveBeenCalledOnce();
    });
});
