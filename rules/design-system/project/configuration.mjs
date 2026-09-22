import path from 'node:path';

const projects = new Map();

/** Register the explicit Angular source root and its actual Tailwind entry. */
export function registerProject(root, cssFile) {
    projects.set(path.resolve(root), { root: path.resolve(root), cssFile: path.resolve(cssFile) });
}

export function projectFor(file) {
    const absolute = path.resolve(file);
    let selected;
    for (const project of projects.values()) {
        const relative = path.relative(project.root, absolute);
        if (relative !== '..' && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative) && (!selected || project.root.length > selected.root.length))
            selected = project;
    }
    return selected ?? null;
}
