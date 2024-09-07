import { AeolusResult } from 'app/entities/programming/aeolus.result';

export class BuildAction {
    name: string;
    runAlways: boolean;
    workdir: string;
    results?: AeolusResult[];
    platform?: string;
    parameters: Map<string, string | boolean | number> = new Map<string, string | boolean | number>();
}

export class ScriptAction extends BuildAction {
    script: string;
}

export class PlatformAction extends BuildAction {
    type: string;
    kind: string;
}
