export class MockNgbModalService {
    open = (component: any, options: any) => ({ componentInstance: {}, result: { then: () => undefined }, close: () => {} });
    hasOpenModals = () => false;
}
