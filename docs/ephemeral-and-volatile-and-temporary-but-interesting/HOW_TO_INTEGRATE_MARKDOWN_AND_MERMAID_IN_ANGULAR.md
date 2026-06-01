# How to Integrate Markdown and Mermaid in Angular

This document describes how this project renders Markdown (including Mermaid diagrams) in the Angular frontend.

The integration centers on the **Wizard** component, which displays certification steps containing Markdown text and optional Mermaid diagrams. Other projects will integrate it into different components.

## Dependencies

The integration relies on two npm packages declared in `src/main/webui/package.json`:

- **`ngx-markdown`** (`^21.3.0`) — Angular library that wraps a Markdown parser and exposes it as components/directives.
- **`mermaid`** (`^11.14.0`) — Diagramming library consumed by `ngx-markdown` to render Mermaid blocks.

## Global Setup

The Markdown provider is configured once at the application level in `src/main/webui/src/app/app.config.ts`:

```typescript
import { MERMAID_OPTIONS, provideMarkdown } from 'ngx-markdown';

export const appConfig: ApplicationConfig = {
  providers: [
    provideMarkdown({
      mermaidOptions: {
        provide: MERMAID_OPTIONS,
        useValue: { startOnLoad: false }
      }
    }),
    // ... other providers
  ]
};
```

Setting `startOnLoad: false` is intentional: Angular controls rendering lifecycle, so Mermaid is triggered manually via the `MarkdownComponent` rather than on page load.

## Using Markdown in Components

### 1. Component Imports

Import either `MarkdownComponent` or `MarkdownModule` depending on the component style:

- **Standalone component** (wizard): imports `MarkdownComponent` directly.
- **Inline template component** (chat window): imports `MarkdownModule`.

### 2. Render Plain Markdown

Bind a string to the `[data]` input of `markdown`:

```html
<markdown [data]="instr.text"></markdown>
```

This is used for:
- Instruction text (`wizard.component.html`)
- Instruction notes (`wizard.component.html`)
- AI assistant responses (`chat-window.component.ts`)

### 3. Render Mermaid Diagrams

Enable Mermaid support by adding the `mermaid` attribute and passing `mermaidOptions`:

```html
<markdown
  mermaid
  [mermaidOptions]="mermaidOptions()"
  [data]="'```mermaid\n' + instr.mermaid + '\n```'">
</markdown>
```

The `mermaidOptions` signal switches the Mermaid theme between `dark` and `default` based on the active UI theme (`ThemeService`).

## Data Model

The backend stores optional Mermaid source in `BackendInstruction.mermaidDiagram`. The frontend controller (`certification.controller.ts`) maps this field to `WizardInstruction.mermaid`:

```typescript
instructions: sortedInstructions.map(instr => ({
  id: instr.id,
  text: instr.text,
  command: instr.command,
  note: instr.note,
  mermaid: instr.mermaidDiagram
}))
```

## Styling

Mermaid diagrams are styled in `src/main/webui/src/app/core/wizard/wizard.component.scss`:

```scss
.mermaid-diagram {
  margin: 1rem 0;
  padding: 1rem;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-primary);
  border-radius: 8px;
  overflow-x: auto;

  ::ng-deep svg {
    width: 100% !important;
    height: auto !important;
  }
}
```

The `::ng-deep` combinator ensures SVG sizing rules pierce the shadow DOM of the `markdown` component.

## Key Files

| File | Role |
|------|------|
| `src/main/webui/src/app/app.config.ts` | Global `provideMarkdown` configuration |
| `src/main/webui/src/app/core/wizard/wizard.component.ts` | Imports `MarkdownComponent`, defines theme-aware `mermaidOptions` |
| `src/main/webui/src/app/core/wizard/wizard.component.html` | Renders instructions and Mermaid diagrams via `<markdown>` |
| `src/main/webui/src/app/core/wizard/wizard.component.scss` | Styles for `.mermaid-diagram` |
| `src/main/webui/src/app/core/chat/chat-window.component.ts` | Imports `MarkdownModule` for AI chat messages |
| `src/main/webui/src/app/certification/certification.controller.ts` | Maps `mermaidDiagram` from backend to frontend model |
