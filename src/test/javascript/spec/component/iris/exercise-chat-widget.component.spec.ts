import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseChatWidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockPipe } from 'ng-mocks';
import { MatDialogModule } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { of } from 'rxjs';
import { User } from 'app/core/user/user.model';

class MockLocalStorageService {
    private storage: { [key: string]: string } = {};

    getItem(key: string): string | null {
        return this.storage[key] || null;
    }

    setItem(key: string, value: string): void {
        this.storage[key] = value;
    }
}

class MockAccountService {
    identity(): Promise<User | null> {
        return Promise.resolve(null);
    }
}

describe('ExerciseChatWidgetComponent', () => {
    let component: ExerciseChatWidgetComponent;
    let fixture: ComponentFixture<ExerciseChatWidgetComponent>;

    beforeEach(async () => {
        jest.spyOn(console, 'error').mockImplementation();

        await TestBed.configureTestingModule({
            imports: [FormsModule, FontAwesomeModule, MatDialogModule],
            declarations: [ExerciseChatWidgetComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                // Provide the mock implementation of LocalStorageService
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                // Add other dependencies required by ExerciseChatWidgetComponent
                { provide: ActivatedRoute, useValue: { url: of([{ path: 'chat' }]) } },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseChatWidgetComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should add user message on send', () => {
        component.newMessage = 'Hello';

        component.onSend();

        expect(component.userMessages).toContain('Hello');
    });

    it('should clear newMessage on send', () => {
        component.newMessage = 'Hello';

        component.onSend();

        expect(component.newMessage).toBe('');
    });
});
