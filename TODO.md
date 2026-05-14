# abstrapact TODOs

## Before Each Release

- Upgrade all dependencies and check security issues in GitHub (Dependabot, code-scanning)
- Update docs to describe any changes

## Today


## Tomorrow


## Later (not yet necessary for initial release)


- Replace `src/main/webui/src/app/demo` with project-specific components
- `e2e-tests/pages/TODO.page.ts` needs to be renamed to a feature-specific filename (e.g., `home.page.ts`) once e2e tests are implemented. The file content has been updated, but the name still contains "TODO".
- `scripts/initialisation/fix_other_stuff.py` still contains baseline cleanup logic. Determine whether it should be run to catch any remaining "abstracore" references, or removed now that setup is complete.

- [ ] delete the top of this file that talks about the git hook
- [ ] Update database migration files
- [ ] add a new oauth client to your oauth authorization server like abstrauth
- [ ] remove all references to `demo` in the entire project
- [ ] remove all files with `demo` in their name
- [ ] ensure all TODOs in the code have been fixed

