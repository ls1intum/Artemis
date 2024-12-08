import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseChatbotComponent } from 'app/iris/course-chatbot/course-chatbot.component';
import { IrisBaseChatbotComponent } from 'app/iris/base-chatbot/iris-base-chatbot.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { Router } from '@angular/router';
import { TranslateFakeLoader, TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { SessionStorageService } from 'ngx-webstorage';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

describe('CourseChatbotComponent', () => {
    let component: CourseChatbotComponent;
    let fixture: ComponentFixture<CourseChatbotComponent>;
    let chatService: IrisChatService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                HttpClientTestingModule,
                TranslateModule.forRoot({
                    loader: {
                        provide: TranslateLoader,
                        useClass: TranslateFakeLoader,
                    },
                }),
                CourseChatbotComponent,
            ],
            providers: [
                MockProvider(SessionStorageService),
                MockComponent(IrisBaseChatbotComponent),
                {
                    provide: Router,
                    useValue: {
                        events: of(),
                        createUrlTree: jest.fn(() => ({})),
                        navigateByUrl: jest.fn(),
                        serializeUrl: jest.fn(() => 'mockUrl'),
                    },
                },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ id: '123' }),
                        queryParams: of({}),
                        snapshot: {
                            paramMap: {
                                get: () => '123',
                            },
                        },
                    },
                },
                TranslateService,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseChatbotComponent);
                component = fixture.componentInstance;

                fixture.componentRef.setInput('courseId', 2);

                chatService = TestBed.inject(IrisChatService);
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call switchTo when courseId changes', () => {
        const switchToSpy = jest.spyOn(chatService, 'switchTo');
        fixture.componentRef.setInput('courseId', 4);
        fixture.detectChanges();
        expect(switchToSpy).toHaveBeenCalledWith(ChatServiceMode.COURSE, component.courseId());
    });
});
