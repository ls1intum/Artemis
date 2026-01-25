import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { InfoPanelComponent } from 'app/assessment/shared/info-panel/info-panel.component';
import { Component } from '@angular/core';

@Component({
    selector: 'jhi-test-host',
    template: `
        <jhi-info-panel [panelHeader]="header" [panelDescriptionHeader]="descriptionHeader">
            <p>Test content</p>
        </jhi-info-panel>
    `,
    imports: [InfoPanelComponent],
})
class TestHostComponent {
    header = 'Test Header';
    descriptionHeader?: string;
}

describe('InfoPanelComponent', () => {
    setupTestBed({ zoneless: true });
    let component: InfoPanelComponent;
    let fixture: ComponentFixture<InfoPanelComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(InfoPanelComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('component creation', () => {
        it('should create the component', () => {
            fixture.componentRef.setInput('panelHeader', 'Test Header');
            fixture.detectChanges();

            expect(component).toBeTruthy();
        });
    });

    describe('inputs', () => {
        it('should accept panelHeader input', () => {
            fixture.componentRef.setInput('panelHeader', 'My Panel Header');
            fixture.detectChanges();

            expect(component.panelHeader()).toBe('My Panel Header');
        });

        it('should accept panelDescriptionHeader input', () => {
            fixture.componentRef.setInput('panelHeader', 'Header');
            fixture.componentRef.setInput('panelDescriptionHeader', 'Description Header');
            fixture.detectChanges();

            expect(component.panelDescriptionHeader()).toBe('Description Header');
        });

        it('should allow panelDescriptionHeader to be undefined', () => {
            fixture.componentRef.setInput('panelHeader', 'Header');
            fixture.detectChanges();

            expect(component.panelDescriptionHeader()).toBeUndefined();
        });
    });

    describe('template rendering', () => {
        it('should display panel header', () => {
            fixture.componentRef.setInput('panelHeader', 'Test Panel Header');
            fixture.detectChanges();

            const headerElement = fixture.nativeElement.querySelector('.panel-header');
            expect(headerElement).toBeTruthy();
            expect(headerElement.textContent.trim()).toBe('Test Panel Header');
        });

        it('should have panel-wrapper class', () => {
            fixture.componentRef.setInput('panelHeader', 'Header');
            fixture.detectChanges();

            const wrapper = fixture.nativeElement.querySelector('.panel-wrapper');
            expect(wrapper).toBeTruthy();
        });

        it('should have panel-body for content projection', () => {
            fixture.componentRef.setInput('panelHeader', 'Header');
            fixture.detectChanges();

            const body = fixture.nativeElement.querySelector('.panel-body');
            expect(body).toBeTruthy();
        });
    });

    describe('content projection', () => {
        it('should project content into panel-body', () => {
            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                imports: [TestHostComponent],
            }).compileComponents();

            const hostFixture = TestBed.createComponent(TestHostComponent);
            hostFixture.detectChanges();

            const panelBody = hostFixture.nativeElement.querySelector('.panel-body');
            expect(panelBody).toBeTruthy();
            expect(panelBody.textContent).toContain('Test content');
        });

        it('should display the correct header from host', () => {
            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                imports: [TestHostComponent],
            }).compileComponents();

            const hostFixture = TestBed.createComponent(TestHostComponent);
            hostFixture.componentInstance.header = 'Custom Header';
            hostFixture.detectChanges();

            const headerElement = hostFixture.nativeElement.querySelector('.panel-header');
            expect(headerElement.textContent.trim()).toBe('Custom Header');
        });
    });
});
