export class BuildConfig {
    public dockerImage?: string;
    public commitHashToBuild?: string;
    public branch?: string;
    public programmingLanguage?: string;
    public projectType?: string;
    public scaEnabled?: boolean;
    public sequentialTestRunsEnabled?: boolean;
    public testwiseCoverageEnabled?: boolean;
    public resultPaths?: string[];
}
