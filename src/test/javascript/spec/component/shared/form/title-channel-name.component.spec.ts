import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule, NgForm } from '@angular/forms';
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
            providers: [NgForm],
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

        fixture.whenStable().then(() => {
            const titleInput = fixture.debugElement.query(By.css('#field_title'));
            expect(titleInput).not.toBeNull();
            expect(titleInput.nativeElement.value).toBe(component.title);

            const channelNameInput = fixture.debugElement.query(By.css('#field_channel_name'));
            expect(channelNameInput).not.toBeNull();
            expect(channelNameInput.nativeElement.value).toBe(component.channelName);
        });
    }));

    it('should only display title input field if channel name is hidden', () => {
        component.hideChannelName = true;
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

    it('init prefix if undefined', () => {
        component.ngOnInit();

        expect(component.channelNamePrefix).toBe('');
    });

    it('init channel name based on prefix and title', fakeAsync(() => {
        component.channelNamePrefix = 'prefix-';
        component.title = 'test';

        component.ngOnInit();
        tick();

        expect(component.channelName).toBe('prefix-test');
    }));

    it('init channel name based on prefix if title is undefined', fakeAsync(() => {
        component.channelNamePrefix = 'prefix-';

        component.ngOnInit();
        tick();

        expect(component.channelName).toBe('prefix-');
    }));

    it('remove consecutive/alternating hyphens and spaces from channel name on init', fakeAsync(() => {
        component.channelNamePrefix = '-- ----p ---';
        component.title = '-- -  t--- -- ';

        component.ngOnInit();
        tick();

        expect(component.channelName).toBe('-p-t-');
    }));

    it("don't init channel name if not allowed", fakeAsync(() => {
        component.channelNamePrefix = '-- ---- ---';
        component.title = '-  --- -- ';
        component.initChannelName = false;

        component.ngOnInit();
        tick();

        expect(component.channelName).toBeUndefined();
    }));
});
