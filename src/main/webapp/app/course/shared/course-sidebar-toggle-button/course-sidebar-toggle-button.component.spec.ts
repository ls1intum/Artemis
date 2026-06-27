import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { facSidebar } from 'app/foundation/icons/icons';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CourseSidebarToggleButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseSidebarToggleButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseSidebarToggleButtonComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(CourseSidebarToggleButtonComponent);
    });

    it('should emit toggleSidebar when clicked', () => {
        const emitSpy = vi.spyOn(fixture.componentInstance.toggleSidebar, 'emit');
        fixture.detectChanges();

        fixture.debugElement.query(By.css('.btn-sidebar-collapse')).triggerEventHandler('click');

        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should apply the collapsed class when the sidebar is collapsed', () => {
        fixture.componentRef.setInput('isCollapsed', true);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.btn-sidebar-collapse')).classes['is-collapsed']).toBeTruthy();
    });

    it('should apply the communication class for communication module sidebars', () => {
        fixture.componentRef.setInput('isCommunicationModule', true);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.btn-sidebar-collapse')).classes['is-communication-module']).toBeTruthy();
    });

    it('should render the sidebar and chevron icons', () => {
        fixture.detectChanges();

        const faIconElements = fixture.debugElement.queryAll(By.directive(FaIconComponent));

        expect(faIconElements).toHaveLength(3);
        expect(faIconElements[0].componentInstance.icon()).toBe(facSidebar);
        expect(faIconElements[1].componentInstance.icon()).toBe(faChevronRight);
        expect(faIconElements[2].componentInstance.icon()).toBe(faChevronRight);
    });
});
