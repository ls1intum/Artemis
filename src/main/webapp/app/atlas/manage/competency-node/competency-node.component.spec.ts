import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyNodeComponent, SizeUpdate } from 'app/atlas/manage/competency-node/competency-node.component';
import { CompetencyGraphNodeDTO } from 'app/atlas/shared/entities/learning-path.model';
import { Component } from '@angular/core';
import { getComponentInstanceFromFixture } from 'test/helpers/utils/general-test.utils';

@Component({
    template: '<jhi-learning-path-competency-node [competencyNode]="competencyNode" (onSizeSet)="onSizeSet($event)" />',
    imports: [CompetencyNodeComponent],
})
class WrapperComponent {
    competencyNode: CompetencyGraphNodeDTO;

    onSizeSet(event: SizeUpdate) {}
}

describe('CompetencyNodeComponent', () => {
    let component: WrapperComponent;
    let fixture: ComponentFixture<WrapperComponent>;
    let competencyNodeComponent: CompetencyNodeComponent;
    let onSizeSetSpy: jest.SpyInstance;

    const competencyNode = <CompetencyGraphNodeDTO>{
        id: '1',
        label: 'Competency',
        value: 71,
        valueType: 'MASTERY_PROGRESS',
        softDueDate: new Date(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [WrapperComponent],
            providers: [],
        }).compileComponents();

        fixture = TestBed.createComponent(WrapperComponent);
        component = fixture.componentInstance;
        competencyNodeComponent = getComponentInstanceFromFixture(fixture, 'jhi-learning-path-competency-node') as CompetencyNodeComponent;

        component.competencyNode = competencyNode;

        onSizeSetSpy = jest.spyOn(component, 'onSizeSet');

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize and emit size update', async () => {
        expect(component).toBeTruthy();
        expect(competencyNodeComponent.competencyNode()).toEqual(competencyNode);
        expect(competencyNodeComponent.value()).toBe(71);
        expect(competencyNodeComponent.valueType()).toBe(competencyNode.valueType);
        expect(onSizeSetSpy).toHaveBeenCalled();
    });

    it('should check if competency is green', () => {
        expect(competencyNodeComponent.isGreen()).toBeFalse();
    });

    it('should check if competency is yellow', () => {
        expect(competencyNodeComponent.isYellow()).toBeTrue();
    });

    it('should check if competency is gray', () => {
        expect(competencyNodeComponent.isGray()).toBeFalse();
    });

    it('should set dimensions', () => {
        competencyNodeComponent.setDimensions();
        expect(onSizeSetSpy).toHaveBeenCalled();
    });
});
