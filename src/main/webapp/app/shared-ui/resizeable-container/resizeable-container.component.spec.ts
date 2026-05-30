import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';

describe('ResizeableContainerComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ResizeableContainerComponent;
    let fixture: ComponentFixture<ResizeableContainerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({}).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ResizeableContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
