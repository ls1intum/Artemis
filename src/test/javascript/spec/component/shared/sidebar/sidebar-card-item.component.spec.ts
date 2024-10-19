import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { SidebarCardSize } from 'app/types/sidebar';
import { ArtemisTestModule } from '../../../test.module';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { input, runInInjectionContext } from '@angular/core';

describe('SidebarCardItemComponent', () => {
    let component: SidebarCardItemComponent;
    let fixture: ComponentFixture<SidebarCardItemComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SidebarCardItemComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SidebarCardItemComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display item title', () => {
        const testItem = {
            title: 'testTitle',
            id: 'testId',
            size: 'M' as SidebarCardSize,
            difficulty: DifficultyLevel.EASY,
        };
        component.sidebarItem = testItem;
        fixture.detectChanges();
        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('#test-sidebar-card-title').textContent).toContain(testItem.title);
    });

    it('should format unreadCount correctly when count is less than 99', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.unreadCount = input<number>(45);
            component.ngOnInit();
            expect(component.formattedUnreadCount).toBe('45');
        });
    });

    it('should format unreadCount as "99+" when count exceeds 99', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.unreadCount = input<number>(120);
            component.ngOnInit();
            expect(component.formattedUnreadCount).toBe('99+');
        });
    });
});
