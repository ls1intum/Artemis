import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { SidebarCardSize } from 'app/types/sidebar';
import { ArtemisTestModule } from '../../../test.module';
import { DifficultyLevel } from 'app/entities/exercise.model';

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

    it('should remove the correct prefix from the name when groupKey is in channelTypes', () => {
        const prefixes = ['exercise-', 'lecture-', 'exam-'];
        const channelTypes = ['exerciseChannels', 'lectureChannels', 'examChannels'];

        for (let i = 0; i < prefixes.length; i++) {
            const prefix = prefixes[i];
            const groupKey = channelTypes[i];
            const nameWithPrefix = prefix + 'TestName';

            component.groupKey = groupKey;
            const result = component.removeChannelPrefix(nameWithPrefix);

            expect(result).toBe('TestName');
        }
    });

    it('should not remove the prefix if groupKey is not in channelTypes', () => {
        const nameWithPrefix = 'exercise-TestName';
        component.groupKey = 'otherGroup';
        const result = component.removeChannelPrefix(nameWithPrefix);

        expect(result).toBe(nameWithPrefix);
    });

    it('should not remove the prefix if name does not start with any of the prefixes', () => {
        const nameWithoutPrefix = 'TestName';
        component.groupKey = 'exerciseChannels';
        const result = component.removeChannelPrefix(nameWithoutPrefix);

        expect(result).toBe(nameWithoutPrefix);
    });

    it('should handle empty name input', () => {
        const emptyName = '';
        component.groupKey = 'exerciseChannels';
        const result = component.removeChannelPrefix(emptyName);

        expect(result).toBe('');
    });

    it('should handle undefined name input', () => {
        const undefinedName = undefined as unknown as string;
        component.groupKey = 'exerciseChannels';
        const result = component.removeChannelPrefix(undefinedName);

        expect(result).toBe(undefinedName);
    });

    it('should handle null name input', () => {
        const nullName = null as unknown as string;
        component.groupKey = 'exerciseChannels';
        const result = component.removeChannelPrefix(nullName);

        expect(result).toBe(nullName);
    });
});
