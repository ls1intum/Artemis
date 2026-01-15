import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VerticalProgressBarComponent } from 'app/tutorialgroup/shared/vertical-progress-bar/vertical-progress-bar.component';

describe('VerticalProgressBarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: VerticalProgressBarComponent;
    let fixture: ComponentFixture<VerticalProgressBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();

        fixture = TestBed.createComponent(VerticalProgressBarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should keep borders in range [0,100]', () => {
        fixture.componentRef.setInput('lowerBorder', -20);
        fixture.componentRef.setInput('upperBorder', 120);
        TestBed.tick();
        fixture.detectChanges();
        expect(component.lowerBorderInternal()).toBe(0);
        expect(component.upperBorderInternal()).toBe(100);
    });

    it('should change color', () => {
        fixture.componentRef.setInput('lowerBorder', 20);
        fixture.componentRef.setInput('upperBorder', 40);
        fixture.componentRef.setInput('lowerColor', 'red');
        fixture.componentRef.setInput('intermediateColor', 'orange');
        fixture.componentRef.setInput('upperColor', 'green');
        fixture.componentRef.setInput('animateFilling', false);
        fixture.componentRef.setInput('heightInPixels', 100);
        fixture.componentRef.setInput('widthInPixels', 100);
        // lower area
        fixture.componentRef.setInput('fillLevelInPercent', 10);
        TestBed.tick();
        fixture.detectChanges();
        expect(component.fillColorCSS).toBe('red');
        fixture.componentRef.setInput('fillLevelInPercent', 20);
        TestBed.tick();
        fixture.detectChanges();
        expect(component.fillColorCSS).toBe('red');
        // intermediate area
        fixture.componentRef.setInput('fillLevelInPercent', 21);
        TestBed.tick();
        fixture.detectChanges();
        expect(component.fillColorCSS).toBe('orange');
        fixture.componentRef.setInput('fillLevelInPercent', 30);
        TestBed.tick();
        fixture.detectChanges();
        expect(component.fillColorCSS).toBe('orange');
        fixture.componentRef.setInput('fillLevelInPercent', 39);
        TestBed.tick();
        fixture.detectChanges();
        expect(component.fillColorCSS).toBe('orange');
        // upper area
        fixture.componentRef.setInput('fillLevelInPercent', 40);
        TestBed.tick();
        fixture.detectChanges();
        expect(component.fillColorCSS).toBe('green');
        fixture.componentRef.setInput('fillLevelInPercent', 50);
        TestBed.tick();
        fixture.detectChanges();
        expect(component.fillColorCSS).toBe('green');
    });
});
