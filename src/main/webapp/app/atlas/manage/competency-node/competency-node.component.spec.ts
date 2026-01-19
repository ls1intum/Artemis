import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyNodeComponent } from 'app/atlas/manage/competency-node/competency-node.component';
import { CompetencyGraphNodeDTO } from 'app/atlas/shared/entities/learning-path.model';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('CompetencyNodeComponent', () => {
    setupTestBed({ zoneless: true });
    let component: CompetencyNodeComponent;
    let fixture: ComponentFixture<CompetencyNodeComponent>;
    let sizeUpdateEmitSpy: ReturnType<typeof vi.spyOn>;

    const competencyNode = <CompetencyGraphNodeDTO>{
        id: '1',
        label: 'Competency',
        value: 71,
        valueType: 'MASTERY_PROGRESS',
        softDueDate: new Date(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyNodeComponent],
            providers: [],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyNodeComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('competencyNode', competencyNode);

        sizeUpdateEmitSpy = vi.spyOn(component.onSizeSet, 'emit');

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and emit size update', async () => {
        expect(component).toBeTruthy();
        expect(component.competencyNode()).toEqual(competencyNode);
        expect(component.value()).toBe(71);
        expect(component.valueType()).toBe(competencyNode.valueType);
        expect(sizeUpdateEmitSpy).toHaveBeenCalled();
    });

    it('should check if competency is green', () => {
        expect(component.isGreen()).toBeFalse();
    });

    it('should check if competency is yellow', () => {
        expect(component.isYellow()).toBeTrue();
    });

    it('should check if competency is gray', () => {
        expect(component.isGray()).toBeFalse();
    });

    it('should set dimensions', () => {
        component.setDimensions();
        expect(sizeUpdateEmitSpy).toHaveBeenCalled();
    });
});
