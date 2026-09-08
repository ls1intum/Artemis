# Client migration recipes

## `@Input` to `input()`

```typescript
// before
@Input() course: Course;
@Input() required = false;

// after
readonly course = input.required<Course>();
readonly required = input(false);
```

Reads become calls: `this.course()` rather than `this.course`. In templates, `course()` likewise.

Use `model()` for two-way component bindings.

## `@Output` to `output()`

```typescript
// before
@Output() saved = new EventEmitter<Course>();
this.saved.emit(course);

// after
readonly saved = output<Course>();
this.saved.emit(course);
```

## `@ViewChild` to `viewChild()`

```typescript
// before
@ViewChild('editor') editor: ElementRef;

// after
readonly editor = viewChild.required<ElementRef>('editor');
```

Use `viewChild()` when the child may be absent, `viewChild.required()` when it must exist.

## Constructor injection to `inject()`

```typescript
// before
constructor(private courseService: CourseService) {}

// after
private readonly courseService = inject(CourseService);
```

## `ngOnChanges` to `computed()` or `effect()`

Deriving a value from inputs is a `computed()`:

```typescript
// before
ngOnChanges() {
    this.visibleExercises = this.exercises.filter((e) => e.visible);
}

// after
readonly visibleExercises = computed(() => this.exercises().filter((e) => e.visible));
```

Use `computed()` for derived values and `effect()` for side effects.

If previous-value tracking or lifecycle ordering cannot be expressed without `ngOnChanges`,
explain the constraint in a line-level lint suppression.

## Cloning, and how it interacts with signals

**Replacing independent state.** With default signal equality, return a new reference:

```typescript
const updated = deepClone(current);
updated.field = value;
return updated;
```

The canonical example is `setImageUrl` in `src/main/webapp/app/core/auth/account.service.ts`.

**Preserving nested identity.** Where existing code mutates an object in place,
`equal: () => false` lets the parent signal notify its consumers when re-set to the same reference.
Deep-cloning instead would detach nested associations.

This does not force a child `input()` to update: Angular still compares the whole-object binding
by identity. For a child bound to the whole object, create a new top-level reference with explicit
field assignments while preserving required nested references; `Exam.withSameValues` and
`StudentExam.withSameValues` are the repository examples. See the cloning section of
`documentation/docs/developer/guidelines/client-development.mdx`.

**When the state is not signal-backed.** Build the replacement explicitly, field by field, rather
than reaching for a shallow copy.

**Single-expression copy with overrides.** `cloneWith(x, { a, b })` instead of `{ ...x, a, b }`. The
source is deep-cloned and the overrides are applied by reference.

**Giving a parsed DTO its prototype.** `hydrate(new Course(), dto)` instead of
`Object.assign(new Course(), dto)`.

Full rationale with more examples:
`documentation/docs/developer/guidelines/client-development.mdx`.

## Template control flow

```angular-html
<!-- before -->
<div *ngIf="course">{{ course.title }}</div>
<li *ngFor="let e of exercises; trackBy: trackId">{{ e.title }}</li>

<!-- after -->
@if (course()) {
    <div>{{ course()!.title }}</div>
}
@for (e of exercises(); track e.id) {
    <li>{{ e.title }}</li>
}
```

`@for` requires `track`. It is not optional the way `trackBy` was.

## Colours

```html
<!-- wrong: primitive, Bootstrap class, superseded arbitrary form -->
<span class="text-red-500">…</span>
<span class="text-danger">…</span>
<span class="text-(--danger)">…</span>

<!-- right: semantic token -->
<span class="text-state-danger">…</span>
```

For a component, prefer the TUM UI variant over a utility class on plain markup.

## Verifying a migration

```bash
pnpm run lint
pnpm exec vitest run <path-to-spec>
```

A template-only error will not show up in either. Run a production build when a template changed
structurally:

```bash
pnpm run webapp:prod
```
