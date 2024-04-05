import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { ArtemisTestModule } from '../../../test.module';
import { DifficultyLevel } from 'app/entities/exercise.model';

describe('SidebarCardItemComponent', () => {
    let component: SidebarCardItemComponent;
    let fixture: ComponentFixture<SidebarCardItemComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SidebarCardItemComponent],
        }).compileComponents();
    }));

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
            difficulty: DifficultyLevel.EASY,
        };
        component.sidebarItem = testItem;
        fixture.detectChanges();
        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('#test-sidebar-card-title').textContent).toContain(testItem.title);
    });
});
