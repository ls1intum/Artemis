export interface JPlagSubmission {
    name: string;
    hasErrors: boolean;
    files: string[];
    numberOfTokens: number;
    tokenList: {
        tokens: any[];
    };
}
