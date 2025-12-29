import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { mockExercise } from 'test/helpers/mocks/service/mock-team.service';

// Store callbacks for testing
let resizeStartCallback: ((event: any) => void) | undefined;
let resizeEndCallback: ((event: any) => void) | undefined;
let resizeMoveCallback: ((event: any) => void) | undefined;

// Mock interactjs
vi.mock('interactjs', () => {
    const mockOn = vi.fn((eventName: string, callback: (event: any) => void) => {
        if (eventName === 'resizestart') {
            resizeStartCallback = callback;
        } else if (eventName === 'resizeend') {
            resizeEndCallback = callback;
        } else if (eventName === 'resizemove') {
            resizeMoveCallback = callback;
        }
        return { on: mockOn };
    });

    const mockResizable = vi.fn(() => ({
        on: mockOn,
    }));

    const mockInteract = Object.assign(
        vi.fn(() => ({ resizable: mockResizable })),
        {
            modifiers: {
                restrictSize: vi.fn(() => ({})),
            },
        },
    );

    return { default: mockInteract };
});

describe('CollapsableAssessmentInstructionsComponent', () => {
    setupTestBed({ zoneless: true });
    let component: CollapsableAssessmentInstructionsComponent;
    let fixture: ComponentFixture<CollapsableAssessmentInstructionsComponent>;

    beforeEach(async () => {
        vi.clearAllMocks();
        await TestBed.configureTestingModule({
            schemas: [NO_ERRORS_SCHEMA],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CollapsableAssessmentInstructionsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.componentRef.setInput('readOnly', false);
    });

    it('should receive input properties correctly', () => {
        fixture.componentRef.setInput('isAssessmentTraining', true);
        fixture.componentRef.setInput('showAssessmentInstructions', false);
        fixture.componentRef.setInput('collapsed', true);
        fixture.componentRef.setInput('readOnly', true);

        expect(component.isAssessmentTraining()).toBe(true);
        expect(component.showAssessmentInstructions()).toBe(false);
        expect(component.collapsed()).toBe(true);
        expect(component.readOnly()).toBe(true);
    });

    it('should have default input values', () => {
        expect(component.isAssessmentTraining()).toBe(false);
        expect(component.showAssessmentInstructions()).toBe(true);
        expect(component.collapsed()).toBe(false);
    });

    it('should expose icons', () => {
        expect(component.faChevronRight).toBeDefined();
        expect(component.faChevronLeft).toBeDefined();
        expect(component.faGripLinesVertical).toBeDefined();
        expect(component.farListAlt).toBeDefined();
    });

    it('should run ngAfterViewInit without errors', () => {
        fixture.detectChanges();
        expect(() => component.ngAfterViewInit()).not.toThrow();
    });

    it('should update collapsed model', () => {
        expect(component.collapsed()).toBe(false);
        component.collapsed.set(true);
        expect(component.collapsed()).toBe(true);
    });

    it('should toggle collapsed state', () => {
        expect(component.collapsed()).toBe(false);
        component.collapsed.set(true);
        expect(component.collapsed()).toBe(true);
        component.collapsed.set(false);
        expect(component.collapsed()).toBe(false);
    });

    it('should handle exercise input', () => {
        expect(component.exercise()).toEqual(mockExercise);
    });

    describe('resize callbacks', () => {
        beforeEach(() => {
            fixture.detectChanges();
            component.ngAfterViewInit();
        });

        it('should add card-resizable class on resizestart', () => {
            expect(resizeStartCallback).toBeDefined();
            const mockTarget = document.createElement('div');
            const mockEvent = { target: mockTarget };

            resizeStartCallback!(mockEvent);

            expect(mockTarget.classList.contains('card-resizable')).toBe(true);
        });

        it('should remove card-resizable class on resizeend', () => {
            expect(resizeEndCallback).toBeDefined();
            const mockTarget = document.createElement('div');
            mockTarget.classList.add('card-resizable');
            const mockEvent = { target: mockTarget };

            resizeEndCallback!(mockEvent);

            expect(mockTarget.classList.contains('card-resizable')).toBe(false);
        });

        it('should update width on resizemove', () => {
            expect(resizeMoveCallback).toBeDefined();
            const mockTarget = document.createElement('div');
            const mockEvent = {
                target: mockTarget,
                rect: { width: 500 },
            };

            resizeMoveCallback!(mockEvent);

            expect(mockTarget.style.width).toBe('500px');
        });
    });
});
