// Adapted from shadcn-ui/lint sites/collect.ts (MIT); see LICENSE.
// Angular resolves component fields before these normalized expressions reach the rules.

export function isClassAttribute(name) {
    return /^(class:list|[^:]*class(name)?s?)$/i.test(name);
}

export function classSiteVisitors(context, options, emit) {
    return context.sourceCode.parserServices.designSystem.classSiteVisitors(context, options, emit);
}

function keyName(property) {
    if (!property.computed && property.key?.type === 'Identifier') return property.key.name;
    if (property.key?.type === 'Literal') return String(property.key.value);
    if (property.key?.type === 'TemplateLiteral' && !property.key.expressions.length) return property.key.quasis[0]?.value?.cooked ?? null;
    return null;
}

export function resolveObject(node) {
    return node?.type === 'ObjectExpression' ? node : null;
}

// Preserve source order and unknown writes: an opaque spread is not an empty object.
export function objectEntries(object, context, path, depth = 0) {
    const entries = [];
    for (const property of object.properties) {
        if (property.type === 'SpreadElement') {
            const inner = depth < 4 ? resolveObject(property.argument) : null;
            if (inner) entries.push(...objectEntries(inner, context, path, depth + 1));
            else entries.push({ unknown: property });
            continue;
        }
        if (property.type !== 'Property') {
            entries.push({ unknown: property });
            continue;
        }
        const key = keyName(property);
        entries.push(key === null ? { unknown: property } : { key, value: property.value });
    }
    return entries;
}

// The last known assignment wins; a subsequent unreadable spread makes it uncertain.
export function resolveProperty(object, key, context, path) {
    let value;
    let uncertain = false;
    for (const entry of objectEntries(object, context, path)) {
        if ('unknown' in entry) uncertain = true;
        else if (entry.key === key) {
            value = entry.value;
            uncertain = false;
        }
    }
    return { value, uncertain };
}

export function resolveMemberValue(node, context, path) {
    const key = keyName({ key: node.property, computed: node.computed });
    const object = key === null ? null : resolveObject(node.object);
    const found = object ? resolveProperty(object, key, context, path) : { uncertain: true };
    return found.uncertain || found.value === undefined ? { key, unresolved: node } : { key, value: found.value };
}
