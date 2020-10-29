export class MockRouter {
    url: string;
    setUrl = (url: string) => (this.url = url);
    navigateByUrl = (url: string) => true;
    navigate = (commands: any[]) => true;
}
