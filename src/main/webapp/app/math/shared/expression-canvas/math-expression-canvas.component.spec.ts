import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { MathExpressionCanvasComponent } from 'app/math/shared/expression-canvas/math-expression-canvas.component';
import { MathNodeContext } from 'app/math/manage/update/math-math-node/math-math-node.component';
import { MathNode } from 'app/math/shared/entities/math-node.model';

// Stub child component to break MathNodeComponent's recursive self-import (mocking it via ng-mocks loops).
@Component({ selector: 'jhi-math-node', standalone: true, template: '' })
class MathNodeStub {}

describe('MathExpressionCanvasComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathExpressionCanvasComponent;

    const dummyCtx: MathNodeContext = {
        draggingBlockType: undefined,
        editable: true,
        selectFn: () => {},
        dropFn: () => {},
        valueChangeFn: () => {},
        removeFn: () => {},
        isSelectedFn: () => false,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MathExpressionCanvasComponent],
        }).overrideComponent(MathExpressionCanvasComponent, {
            set: { imports: [MathNodeStub], template: '' },
        });

        const fixture = TestBed.createComponent(MathExpressionCanvasComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('rootNodes', [{ type: 'var', value: 'x' } as MathNode]);
        fixture.componentRef.setInput('nodeCtx', dummyCtx);
    });

    it('creates with the expected root nodes', () => {
        expect(component).toBeTruthy();
        expect(component.rootNodes().length).toBe(1);
        expect(component.gaps().length).toBe(2);
    });

    it('emits gapDrop with the payload when a block is dropped', () => {
        const emitSpy = vi.fn();
        component.gapDrop.subscribe(emitSpy);

        const dataTransfer = { getData: () => 'add' } as unknown as DataTransfer;
        const event = { dataTransfer, preventDefault: () => {} } as unknown as DragEvent;
        component.onGapDrop(event, 1);

        expect(emitSpy).toHaveBeenCalledWith({ gapIndex: 1, payload: 'add' });
        expect(component.dragHoveredGapIndex()).toBeUndefined();
    });

    it('sets dragHoveredGapIndex on dragover', () => {
        const dataTransfer = { dropEffect: 'none' } as unknown as DataTransfer;
        const event = { dataTransfer, preventDefault: () => {} } as unknown as DragEvent;
        component.onGapDragOver(event, 0);

        expect(component.dragHoveredGapIndex()).toBe(0);
    });
});
