/** A maintainer as listed in about-us.json. An installation can override that file in its public/content directory. */
export interface AboutUsMaintainer {
    fullName: string;
    photoDirectory: string;
    role?: string;
    website?: string;
}

export interface AboutUsModel {
    projectManagers?: AboutUsMaintainer[];
}
