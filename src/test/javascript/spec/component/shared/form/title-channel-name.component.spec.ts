import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule, NgForm } from '@angular/forms';
import { By } from '@angular/platform-browser';

import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

describe('TitleChannelNameComponent', () => {
    let component: TitleChannelNameComponent;
    let fixture: ComponentFixture<TitleChannelNameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, ArtemisSharedComponentModule],
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

        const newTitle = 'New 0123 @()[]{} !?.-_ $%& too long name that is more than 30 characters';
        const titleInput = fixture.debugElement.query(By.css('#field_title'));
        titleInput.nativeElement.value = newTitle;
        titleInput.nativeElement.dispatchEvent(new Event('input'));

        fixture.detectChanges();
        tick();

        expect(component.title).toBe(newTitle);
        expect(component.channelName).toBe('new-0123-too-long-name-that-is');
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

    it('remove special characters and trailing hyphens from channel name on init with non-empty title', fakeAsync(() => {
        component.channelNamePrefix = '-- -!?-p --()';
        component.title = '-- -  t--=*+ -- ';

        component.ngOnInit();
        tick();

        expect(component.channelName).toBe('-p-t');
    }));

    it("don't remove trailing hyphens from channel name on init with empty title", fakeAsync(() => {
        component.channelNamePrefix = '-- -!?-p --()';
        component.title = '';

        component.ngOnInit();
        tick();

        expect(component.channelName).toBe('-p-');
    }));

    it("don't remove trailing hyphens from channel name on init with undefined title", fakeAsync(() => {
        component.channelNamePrefix = '-- -!?-p --()-';
        component.title = undefined;

        component.ngOnInit();
        tick();

        expect(component.channelName).toBe('-p-');
    }));

    it('remove trailing hyphens from channel name on title edit', fakeAsync(() => {
        component.channelNamePrefix = '-- -!?-p --()';

        component.updateTitle('--t--(%&');
        tick();

        expect(component.channelName).toBe('-p-t');
    }));

    it("don't remove trailing hyphens from channel name on title edit if title empty", fakeAsync(() => {
        component.channelNamePrefix = '-- -!?-p --()';

        component.updateTitle('');
        tick();

        expect(component.channelName).toBe('-p-');
    }));

    it("don't remove trailing hyphens from channel name on channel name edit", fakeAsync(() => {
        component.channelNamePrefix = '-- -!?-p --()-';

        component.formatChannelName('-p--t--');
        tick();

        expect(component.channelName).toBe('-p--t--');
    }));

    it("don't init channel name if not allowed", fakeAsync(() => {
        component.channelNamePrefix = 'p-';
        component.title = 't';
        component.initChannelName = false;

        component.ngOnInit();
        tick();

        expect(component.channelName).toBeUndefined();
    }));
});
