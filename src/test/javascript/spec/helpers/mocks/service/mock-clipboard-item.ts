export class MockClipboardItem {
    types: string[];

    getType(_type: string): Promise<Blob> {
        return Promise.resolve(new Blob());
    }

    presentationStyle: PresentationStyle;
}
