import { empty, Observable } from 'rxjs';

export class MockRouter {
    url: string;
    setUrl = (url: string) => (this.url = url);
    navigateByUrl = (url: string) => true;
    navigate = (commands: any[]) => true;
    events: Observable<Event> = empty();
}
