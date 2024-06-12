import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyNodeComponent } from 'app/course/learning-paths/components/competency-node/competency-node.component';
import { CompetencyGraphNodeDTO } from 'app/entities/competency/learning-path.model';

describe('CompetencyNodeComponent', () => {
    let component: CompetencyNodeComponent;
    let fixture: ComponentFixture<CompetencyNodeComponent>;
    let sizeUpdateEmitSpy: jest.SpyInstance;

    const competencyNode = {
        id: '1',
        label: 'Competency',
        confidence: 30,
        masteryProgress: 71.44,
        progress: 80,
        softDueDate: new Date(),
    } as CompetencyGraphNodeDTO;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyNodeComponent],
            providers: [],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyNodeComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('competencyNode', competencyNode);

        sizeUpdateEmitSpy = jest.spyOn(component.onSizeSet, 'emit');

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize and emit size update', async () => {
        expect(component).toBeTruthy();
        expect(component.competencyNode()).toEqual(competencyNode);
        expect(sizeUpdateEmitSpy).toHaveBeenCalled();
    });

    it('should calculate rounded mastery', () => {
        expect(component.masteryProgress()).toBe(71);
    });

    it('should check if competency is mastered', () => {
        expect(component.isMastered()).toBeFalse();
    });

    it('should check if competency is started', () => {
        expect(component.isStarted()).toBeTrue();
    });

    it('should check if competency is not started', () => {
        expect(component.isNotStarted()).toBeFalse();
    });

    it('should set dimensions', () => {
        component.setDimensions();
        expect(sizeUpdateEmitSpy).toHaveBeenCalled();
    });
});
