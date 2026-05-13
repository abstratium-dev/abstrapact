# abstracore

----
ONLY if modifying this for your own company:

  FIRST: check that the file `.git/hooks/pre-commit` exists and is executable. If not, move the script `copy_to_.git_hooks_pre-commit` to there.

  THEN: read more below.

OTHERWISE:

  REMOVE the `copy_to_.git_hooks_pre-commit` file and delete this section

----

## Windsurf Hooks Installation

This repository includes optional Windsurf hooks for enhanced safety. To install them:

```bash
bash scripts/windsurf-hooks/install-hooks.sh
```

This will copy the hook scripts to `~/.codeium/abstratium-hooks/`. The install script will also provide instructions for configuring Windsurf to use these hooks.

----

**abstracore** is the master blueprint for abstratium applications. Built on the Quarkus subatomic Java stack, Quinoa for seamless integration, and Angular for the frontend, it serves as the upstream source for all specific project forks.

## 📦 Tech Stack

Runtime: Quarkus (Java)

Frontend UI: Angular (via Quinoa)

API Layer: REST

Auth: Integrated with Abstrauth

Data: Designed for MySql compatibility

## 🛠️ Getting Started

1. Creating a New Project from Abstracore

To start a new project (e.g., abstradex) using this core:

Create a new empty repository on your Git server.

Clone Abstracore and point it to your new origin:

```bash
git clone https://github.com/abstratium-dev/abstracore.git your-new-project
cd your-new-project
git remote rename origin upstream
git remote add origin git@github.com:your-org/your-new-project.git
git push -u origin main
```

2. Pulling Baseline Updates

When Abstracore is updated with new features or security patches, pull those changes into your project fork using the provided sync script:

```bash
# From the project root, run the sync script
bash scripts/sync-base.sh
```

The script will:
- Add the `upstream` remote if it doesn't exist (pointing to Abstracore)
- Fetch the latest changes from Abstracore
- Merge the baseline changes into your project
- Pause before committing so you can review the changes

**Manual Alternative:**

If you prefer to sync manually:

```bash
# Ensure you are on your main branch
git checkout main

# Add upstream remote (only needed once)
git remote add upstream git@github.com:abstratium-dev/abstracore.git

# Fetch the latest baseline code
git fetch upstream

# Merge baseline changes into your project
git merge upstream/main --no-commit --no-ff

# Review changes, then commit
git commit -m "Merge baseline updates from Abstracore"
```

⚠️ **IMPORTANT**: Avoid modifying the `/core` directory in your project forks. Keep your custom logic in `/app` or specific feature packages to minimize merge conflicts during updates.

## 🏗️ Project Structure

src/main/java/...: Core logic, security filters, and Abstrauth integration.

src/main/webui: The Angular application (managed by Quinoa).

docker/: Standardized deployment configurations.

scripts/: Automation for syncing with Abstracore.

## 🚀 Development Mode

Run the following command to start Quarkus in Dev Mode with the Angular live-reload server:

```bash
./mvnw quarkus:dev
```
Backend: http://localhost:808x

Frontend: Automatically proxied by Quinoa

Dev UI: http://localhost:808x/q/dev

## 📝 Governance

This is a Living Blueprint. If you develop a feature in a specific project (like a new logging service or UI utility) that would benefit all Abstratium apps, please back-port it to Abstracore via a Pull Request.


------------------------


## Things to remember

- **Backend For Frontend (BFF) Architecture** - This service must act as a BFF if it has a UI. It is the BFF for that UI.
- **Native Builds** - This service must be built as a native image (GraalVM) for optimal performance and low footprint.
- **Low footprint** - uses as little as 64MB RAM and a small amount of CPU for typical workloads, idles at near zero CPU, achieved by being built as a native image (GraalVM)
- **Based on Quarkus and Angular** - industry standard frameworks

## Security

🔒 **Found a security vulnerability?** Please read our [Security Policy](SECURITY.md) for responsible disclosure guidelines.

For information about the security implementation and features, see [SECURITY_DESIGN.md](docs/security/SECURITY_DESIGN.md).

## Documentation

- [User Guide](USER_GUIDE.md)
- [Database](docs/DATABASE.md)
- [Native Image Build](docs/NATIVE_IMAGE_BUILD.md)
- [Other documentation](docs)

## Running the Application

See [User Guide](USER_GUIDE.md)

## Development and Testing

See [Development and Testing](docs/DEVELOPMENT_AND_TESTING.md)

## TODO

See [TODO.md](TODO.md)


## Aesthetics

### favicon

https://favicon.io/favicon-generator/ - text based

Text: a
Background: rounded
Font Family: Leckerli One
Font Variant: Regular 400 Normal
Font Size: 110
Font Color: #FFFFFF
Background Color: #5c6bc0

----

# Things to do when creating a new project

- [ ] - decide on a new port number for this service and change it
  - in `proxy.conf.json`
  - and all the TODO comments that tell you to do that 
  - and all the places that use (see https://github.com/abstratium-dev/abstrerp/blob/main/README.md for next available ports and update that table)
    - `808x`
    - `900x`
    - `420x`
  - in `package.json` of `src/main/webui` where it tells `ng serve` which port number to use
    - `"start": "ng serve --proxy-config proxy.conf.json --port=4201",` <<< THERE!
- [ ] - Use the prompt below, to get an LLM to do this
- [ ] - Search for TODO and fix
- [ ] - Search for core and fix, e.g. in `pom.xml`
- [ ] - Update README.md with project-specific information
- [ ] - Update USER_GUIDE.md with project-specific information
- [ ] - Update DATABASE.md with project-specific information
- [ ] - Update NATIVE_IMAGE_BUILD.md with project-specific information
- [ ] - Update SECURITY_DESIGN.md with project-specific information
- [ ] - Update TODO.md with project-specific information
- [ ] - Update SECURITY.md with project-specific information
- [ ] - Update CONTRIBUTING.md with project-specific information
- [ ] - Create favicon, store it in root as zip and put it in `src/main/webui/public`
- [ ] - Update `.windsurf` configuration
- [ ] - Replace `src/main/webui/src/app/demo` with project-specific components
- [ ] - Update application.properties with abstradex-specific values
- [ ] - Update Angular configuration files (angular.json, package.json, index.html)
- [ ] - Update Java source files (Roles.java, ConfigInfoContributor.java)
- [ ] - Update database migration files
- [ ] - Update script files (build-docker-image.sh, push-docker-image.sh, clear-test-db.sh)
- [ ] - Update e2e-tests configuration
- [ ] - Update documentation files (QUARKUS.md, DEVELOPMENT_AND_TESTING.md, AUTHENTICATION_FLOW.md)
- [ ] - delete the top of this file that talks about the git hook
- [ ] - delete the file name `copy_to_.git_hooks_pre-commit` as it is only required in the baseline project, and keeping that hook in your new project would break the mechanism!!!
- [ ] add a new oauth client to your oauth authorization server like abstrauth
- [ ] change the "TODO" at the end of the line for the LABEL in the file `src/main/docker/Dockerfile.native-micro`
- [ ] - delete this TODO list

# First Prompt for LLM 

```
I have copied a "baseline project" that I use to create an initial version of all the microservices in my company. it is full of "TODO" and "todo" markers which show places in the code that need updating.

This project is called "abstradex" and is A "rolodex" for your SME's partners (customers, suppliers, etc.) . Search for all places that contain the text "todo" and make the appropriate changes. 

Also use @README.md#L154-169  as a checklist and as you fix those things, add an X between the square brackets. if anything is missing off of that list, please append it to the list. keep going until you cannot find any more TODOs to fix. 

If there is anything you are unsure of, add that to a new chapter named "FIXME" below the todo list in @README.md .  

Please do not change the functionality of the application yet. Keep things like the @Demo.java entity, @DemoService.java , @DemoResource.java  and all the related stuff in the @src/main/webui  folder like @demo.component.ts , etc.  We will change the functionality later!
```

# Second Prompt for LLM

Remember to replace XXXXXX with the name of the entity that you want to replace. Like "partner".

```
Using the description at the top of the @README.md file, replace the @Demo.java entity, @DemoService.java , @DemoResource.java  and all the related stuff in the @src/main/webui  folder like @demo.component.ts , etc.  with a new CRUD service for the XXXXXX entity.

That Entity should have the following properties:

- name
- description
- website
- phone
- email
- address
- city
- state
- zip
- country
```

# TODO later after implementing your first feature

- [ ] remove all references to `demo` in the entire project
- [ ] remove all files with `demo` in their name
