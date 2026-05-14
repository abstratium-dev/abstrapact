# abstrapact

Abstrapact is an application that deals with products, contracts and sales processes. See the [user guide](./USER_GUIDE.md) for more information.

## Windsurf Hooks Installation

This repository includes optional Windsurf hooks for enhanced safety. To install them:

```bash
bash scripts/windsurf-hooks/install-hooks.sh
```

This will copy the hook scripts to `~/.codeium/abstratium-hooks/`. The install script will also provide instructions for configuring Windsurf to use these hooks.

----

## 📦 Tech Stack

Runtime: Quarkus (Java)

Frontend UI: Angular (via Quinoa)

API Layer: REST

Auth: Integrated with Abstrauth

Data: Designed for MySql compatibility

## Pulling Baseline Updates

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
Backend: http://localhost:8088

Frontend: Automatically proxied by Quinoa

Dev UI: http://localhost:8088/q/dev

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
