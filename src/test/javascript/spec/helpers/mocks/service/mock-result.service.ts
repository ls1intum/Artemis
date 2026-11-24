import { EMPTY } from 'rxjs';
import { IResultService } from 'app/exercise/result/result.service';

export class MockResultService implements IResultService {
    triggerDownloadCSV = (rows: string[], csvFileName: string) => EMPTY;
}
