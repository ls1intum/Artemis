import { HttpRequest } from '@angular/common/http';
import { BrowserFingerprintInterceptor } from 'app/core/interceptor/browser-fingerprint.interceptor.service';
import { of } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

describe(`BrowserFingerprintInterceptor`, () => {
    let fingerprintInterceptor: BrowserFingerprintInterceptor;

    const fingerprint = '123456789012345';
    const instanceIdentifier = 'abcdefgh';

    const browserFingerPrintServiceMock = {
        browserFingerprint: of(fingerprint),
        browserInstanceId: of(instanceIdentifier),
    } as any as BrowserFingerprintService;

    const falsyBrowserFingerPrintServiceMock = {
        browserFingerprint: of(null),
        browserInstanceId: of(null),
    } as any as BrowserFingerprintService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                BrowserFingerprintInterceptor,
                {
                    provide: BrowserFingerprintService,
                    useValue: browserFingerPrintServiceMock,
                },
            ],
        });

        fingerprintInterceptor = TestBed.inject(BrowserFingerprintInterceptor);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    const testExpectedFingerprintAndInstanceID = (localFingerprint: string, localInstanceIdentifier: string) => {
        const requestMock = new HttpRequest('GET', `test`);
        const cloneSpy = jest.spyOn(requestMock, 'clone');
        const mockHandler = {
            handle: jest.fn(),
        };

        fingerprintInterceptor.intercept(requestMock, mockHandler);

        expect(cloneSpy).toHaveBeenCalledOnce();
        expect(cloneSpy).toHaveBeenCalledWith({
            setHeaders: {
                'X-Artemis-Client-Instance-ID': localInstanceIdentifier,
                'X-Artemis-Client-Fingerprint': localFingerprint,
            },
        });
        expect(mockHandler.handle).toHaveBeenCalledOnce();
    };

    it('should add fingerprint and instance ID if request goes to artemis', () => {
        testExpectedFingerprintAndInstanceID(fingerprint, instanceIdentifier);
    });

    it('should not send headers if fingerprint service returns falsy values', () => {
        // We have to reset the full module to ensure that we use the falsy BrowserFingerprintService
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            providers: [
                BrowserFingerprintInterceptor,
                {
                    provide: BrowserFingerprintService,
                    useValue: falsyBrowserFingerPrintServiceMock,
                },
            ],
        });

        const otherFingerprintInterceptor = TestBed.inject(BrowserFingerprintInterceptor);

        const requestMock = new HttpRequest('GET', `test`);
        const cloneSpy = jest.spyOn(requestMock, 'clone');
        const mockHandler = {
            handle: jest.fn(),
        };

        otherFingerprintInterceptor.intercept(requestMock, mockHandler);

        expect(cloneSpy).not.toHaveBeenCalled();
        expect(mockHandler.handle).toHaveBeenCalledOnce();
        expect(mockHandler.handle).toHaveBeenCalledWith(requestMock);
    });

    it('should do nothing if the request goes elsewhere', () => {
        const requestMock = new HttpRequest('GET', 'https://example.com/test');
        const cloneSpy = jest.spyOn(requestMock, 'clone');
        const mockHandler = {
            handle: jest.fn(),
        };

        fingerprintInterceptor.intercept(requestMock, mockHandler);

        expect(cloneSpy).not.toHaveBeenCalled();
        expect(mockHandler.handle).toHaveBeenCalledOnce();
        expect(mockHandler.handle).toHaveBeenCalledWith(requestMock);
    });
});
