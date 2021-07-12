export class MockFileService {
    downloadFileWithAccessToken = () => {
        return { subscribe: (fn: (value: any) => void) => fn({ body: new Window() }) };
    };
}
