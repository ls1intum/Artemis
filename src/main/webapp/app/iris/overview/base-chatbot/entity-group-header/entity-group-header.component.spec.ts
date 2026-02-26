import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { EntityGroupHeaderComponent } from './entity-group-header.component';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronRight, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { RouterModule } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('EntityGroupHeaderComponent', () => {
    setupTestBed({ zoneless: true });

    let component: EntityGroupHeaderComponent;
    let fixture: ComponentFixture<EntityGroupHeaderComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [EntityGroupHeaderComponent, MockPipe(ArtemisTranslatePipe), RouterModule.forRoot([])],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(EntityGroupHeaderComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function setDefaultInputs(overrides: Record<string, unknown> = {}) {
        fixture.componentRef.setInput('entityName', overrides['entityName'] ?? 'Test Exercise');
        fixture.componentRef.setInput('icon', overrides['icon'] ?? faKeyboard);
        fixture.componentRef.setInput('tooltipKey', overrides['tooltipKey'] ?? 'artemisApp.iris.chatHistory.relatedEntityTooltip.programmingExercise');
        fixture.componentRef.setInput('entityRoute', overrides['entityRoute'] ?? '../exercises/42');
        if ('expanded' in overrides) {
            fixture.componentRef.setInput('expanded', overrides['expanded']);
        }
    }

    it('should create', () => {
        setDefaultInputs();
        expect(component).toBeTruthy();
    });

    it('should render entity name', async () => {
        setDefaultInputs({ entityName: 'My Exercise' });
        await fixture.whenStable();

        const nameEl: HTMLElement = fixture.nativeElement.querySelector('.entity-name');
        expect(nameEl.textContent).toContain('My Exercise');
    });

    it('should render the entity icon', async () => {
        setDefaultInputs({ icon: faKeyboard });
        await fixture.whenStable();

        const iconDebugEl = fixture.debugElement.query(By.css('.entity-icon-link fa-icon'));
        expect(iconDebugEl).toBeTruthy();
        const iconInstance = iconDebugEl.componentInstance as FaIconComponent;
        expect(iconInstance.icon()).toBe(faKeyboard);
    });

    it('should render the chevron icon', async () => {
        setDefaultInputs();
        await fixture.whenStable();

        const chevronDebugEl = fixture.debugElement.query(By.css('.chevron-position'));
        expect(chevronDebugEl).toBeTruthy();
        const iconInstance = chevronDebugEl.componentInstance as FaIconComponent;
        expect(iconInstance.icon()).toBe(faChevronRight);
    });

    it('should emit toggleExpanded on click', async () => {
        setDefaultInputs();
        vi.spyOn(component.toggleExpanded, 'emit');
        await fixture.whenStable();

        const headerDiv = fixture.debugElement.query(By.css('.entity-group-header'));
        headerDiv.triggerEventHandler('click', null);

        expect(component.toggleExpanded.emit).toHaveBeenCalledOnce();
    });

    it('should emit toggleExpanded on Enter key', async () => {
        setDefaultInputs();
        vi.spyOn(component.toggleExpanded, 'emit');
        await fixture.whenStable();

        const headerDiv = fixture.debugElement.query(By.css('.entity-group-header'));
        headerDiv.triggerEventHandler('keydown.enter', null);

        expect(component.toggleExpanded.emit).toHaveBeenCalledOnce();
    });

    it('should emit toggleExpanded on Space key', async () => {
        setDefaultInputs();
        vi.spyOn(component.toggleExpanded, 'emit');
        await fixture.whenStable();

        const headerDiv = fixture.debugElement.query(By.css('.entity-group-header'));
        headerDiv.triggerEventHandler('keydown.space', null);

        expect(component.toggleExpanded.emit).toHaveBeenCalledOnce();
    });

    it('should not trigger toggle when icon link is clicked', async () => {
        setDefaultInputs();
        vi.spyOn(component.toggleExpanded, 'emit');
        await fixture.whenStable();

        const iconLink = fixture.debugElement.query(By.css('.entity-icon-link'));
        const mockEvent = new Event('click');
        vi.spyOn(mockEvent, 'stopPropagation');
        iconLink.triggerEventHandler('click', mockEvent);

        expect(mockEvent.stopPropagation).toHaveBeenCalled();
    });

    it('should add rotated class when expanded is true', async () => {
        setDefaultInputs({ expanded: true });
        await fixture.whenStable();

        const chevronEl = fixture.debugElement.query(By.css('.chevron-position'));
        expect(chevronEl.nativeElement.classList).toContain('rotated');
    });

    it('should not add rotated class when expanded is false', async () => {
        setDefaultInputs({ expanded: false });
        await fixture.whenStable();

        const chevronEl = fixture.debugElement.query(By.css('.chevron-position'));
        expect(chevronEl.nativeElement.classList).not.toContain('rotated');
    });

    it('should set aria-expanded to true when expanded', async () => {
        setDefaultInputs({ expanded: true });
        await fixture.whenStable();

        const headerDiv: HTMLElement = fixture.nativeElement.querySelector('.entity-group-header');
        expect(headerDiv.getAttribute('aria-expanded')).toBe('true');
    });

    it('should set aria-expanded to false when collapsed', async () => {
        setDefaultInputs({ expanded: false });
        await fixture.whenStable();

        const headerDiv: HTMLElement = fixture.nativeElement.querySelector('.entity-group-header');
        expect(headerDiv.getAttribute('aria-expanded')).toBe('false');
    });

    it('should render the icon link as an anchor element with routerLink', async () => {
        setDefaultInputs({ entityRoute: '../exercises/42' });
        await fixture.whenStable();

        const iconLink = fixture.debugElement.query(By.css('a.entity-icon-link'));
        expect(iconLink).toBeTruthy();
        expect(iconLink.nativeElement.tagName).toBe('A');
    });
});
