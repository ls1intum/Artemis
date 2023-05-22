import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';

import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { ArtemisTestModule } from '../../../test.module';

describe('TitleChannelNameComponent', () => {
    let component: TitleChannelNameComponent;
    let fixture: ComponentFixture<TitleChannelNameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [TitleChannelNameComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TitleChannelNameComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display title and channel name input fields with correct content', fakeAsync(() => {
        component.title = 'Test';
        component.channelName = 'test';

        fixture.detectChanges();
        tick();

        const titleInput = fixture.debugElement.query(By.css('#field_title'));
        expect(titleInput).not.toBeNull();
        expect(titleInput.nativeElement.value).toEqual(component.title);

        const channelNameInput = fixture.debugElement.query(By.css('#field_channel_name'));
        expect(channelNameInput).not.toBeNull();
        expect(channelNameInput.nativeElement.value).toEqual(component.channelName);
    }));

    it('should only display title input field for undefined title', () => {
        fixture.detectChanges();

        const titleInput = fixture.debugElement.query(By.css('#field_title'));
        expect(titleInput).not.toBeNull();

        const channelNameInput = fixture.debugElement.query(By.css('#field_channel_name'));
        expect(channelNameInput).toBeNull();
    });

    it('should only display title input field for undefined channel name', () => {
        component.title = 'Test';

        fixture.detectChanges();

        const titleInput = fixture.debugElement.query(By.css('#field_title'));
        expect(titleInput).not.toBeNull();

        const channelNameInput = fixture.debugElement.query(By.css('#field_channel_name'));
        expect(channelNameInput).toBeNull();
    });

    it('should update channel name on title change', fakeAsync(() => {
        component.title = 'Test';
        component.channelName = 'test';
        fixture.detectChanges();
        tick();

        const newTitle = 'New 0123 @()[]{} !?.-_ $%& too long';
        const titleInput = fixture.debugElement.query(By.css('#field_title'));
        titleInput.nativeElement.value = newTitle;
        titleInput.nativeElement.dispatchEvent(new Event('input'));

        fixture.detectChanges();
        tick();

        expect(component.title).toBe(newTitle);
        expect(component.channelName).toBe('new-0123-@()[]{}-!?.-_-$%&-too');
    }));

    it('set channel name to prefix if empty', () => {
        const prefix = 'test-';
        component.channelName = '';
        component.channelNamePrefix = prefix;

        component.ngOnInit();

        expect(component.channelNamePrefix).toBe(prefix);
        expect(component.channelName).toBe(prefix);
    });

    it('init prefix if undefined without influencing channel name', () => {
        component.channelName = 'test';

        component.ngOnInit();

        expect(component.channelNamePrefix).toBe('');
        expect(component.channelName).toBe('test');
    });
});
