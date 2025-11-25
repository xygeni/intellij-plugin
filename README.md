# intellij-plugin

![Build](https://github.com/xygeni/intellij-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/xygeni.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/xygeni)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Get familiar with the [template documentation][template].
- [ ] Adjust the [pluginGroup](./gradle.properties) and [pluginName](./gradle.properties), as well as the [id](./src/main/resources/META-INF/plugin.xml) and [sources package](./src/main/kotlin).
- [ ] Adjust the plugin description in `README` (see [Tips][docs:plugin-description])
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the `MARKETPLACE_ID` in the above README badges. You can obtain it once the plugin is published to JetBrains Marketplace.
- [ ] Set the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.
- [ ] Configure the [CODECOV_TOKEN](https://docs.codecov.com/docs/quick-start) secret for automated test coverage reports on PRs

<!-- Plugin description -->
# Xygeni Security Plugin

**Secure your codebase with Secrets, SAST, SCA, IaC & Supply Chain scanning directly within your VS Code environment.**

**Xygeni Security Scanner** is a powerful extension that brings comprehensive security scanning to your fingertips. It integrates seamlessly with your development workflow, allowing you to identify and remediate security vulnerabilities early in the process.

## Key features:
* **Comprehensive Scanning**: Detects a wide range of security issues:
  * **_Secrets_**: Find hardcoded credentials, API keys, and other sensitive data.
  * **_SAST (Static Application Security Testing)_**: Analyze your source code for common vulnerabilities.
  * **_SCA (Software Composition Analysis)_**: Identify vulnerabilities in your open-source dependencies.
  * **_IaC (Infrastructure as Code)_**: Scan your IaC files (e.g., Terraform, CloudFormation) for misconfigurations.
  * **_Misconfigurations_**: Detect security misconfigurations in your application and services.
* **Seamless Integration**: The extension adds a dedicated Xygeni view to your activity bar for easy access.
* **Guided Setup**: A simple configuration process to connect to the Xygeni service.
* **_In-Editor Issue Highlighting_**: View security findings directly in your code, making it easy to pinpoint and fix issues.
* **_Detailed Vulnerability Information_**: Get rich details for each identified issue, including severity, description, and remediation guidance.


## Getting started:

1. **Install the plugin** from [JetBrains marketplace](https://plugins.jetbrains.com/search?search=xygeni). Once installed, the plugin automatically downloads and sets up the **Xygeni Scanner**
2. **Open the Xygeni View and the Xygeni console**: After installation, click the Xygeni icon in the activity bar to open the view and console.
3. **Configure the plugin**:
   - You will be prompted to configure the connection to the Xygeni service.
   - Obtain an API token from your [**Xygeni Dashboard**](https://in.xygeni.io/auth/login). If you don’t have an account, you can sign up for a free trial.
   - Enter the **Xygeni API URL** and your **API token** in the configuration view.
4. **Run a scan**.
   - Once configured, the scan is ready
   - Click on the **Run scan** button to initiate a scan of your workspace
5. **View results**: 
    - Scan results are displayed in the Xygeni view, categorized by type (SAST, SCA, Secrets, etc.).
    - Click an issue to view the file where the issue is found and 
    - Click twice on an issue to view detailed information.
6. **Fix issues**: 
    - On the detailed information, select the *FIX* tab to remediate the vulnerability.
   
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "intellij-plugin"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/xygeni/intellij-plugin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


## How to publish de Plugin
[How to publish a plugin manually](./publish.md)
[Publication procedure](./publication_pocedure.md)

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
