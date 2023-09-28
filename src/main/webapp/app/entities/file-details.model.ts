export class FileDetails {
    constructor(
        public name: string = '',
        public extension: string = '',
    ) {}

    /**
     * Extracts the FileDetails from a path.
     * @param filePath a unique path, e.g. following the pattern {index}_{nameAndExtension}.
     */
    static getFileDetailsFromPath(filePath: string | undefined): FileDetails {
        if (!filePath) {
            return new FileDetails('N/A', 'N/A');
        } else {
            const filePathSplit: string[] = filePath.split('/');
            const uniqueFileName = filePathSplit.last()!;
            const actualFileName = uniqueFileName.substring(uniqueFileName.indexOf('_') + 1);

            const fileNameSplit: string[] = actualFileName.split('.');
            const fileExtension = fileNameSplit.last()!;

            return new FileDetails(actualFileName, fileExtension);
        }
    }
}
