export class MockWindowRef {
    mockUserAgent = 'MockUserAgent';

    get nativeWindow(): any {
        return { navigator: { userAgent: this.mockUserAgent } };
    }
}
