<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-plugin Changelog
All notable changes to this project will be documented in this file.



## [1.6.2] - 2026-09-09
### Added
- API Security scan: API flaws are listed in a new "API Security" section, with endpoint, module, service, OWASP API Top 10 and CWE details (xygeni/xygeni-product-backlog#1691).
- AI Security scan: AI findings are listed in a new "AI Security" section, with the AI asset kind, the standards they map to (OWASP LLM / ASI Top 10) and their red-team vectors (xygeni/xygeni-product-backlog#1692).
- AI Security findings can be fixed with the Xygeni Agent (FIX IT tab, scanner `util rectify --ai`) (xygeni/xygeni-product-backlog#1692).
- API Security findings are listed by flaw type, like the other categories; the full title is shown in the details (xygeni/xygeni-product-backlog#1691).
### Fixed
- Startup no longer fails on IDEs without the JCEF module (`NoClassDefFoundError: com.intellij.ui.jcef.JBCefApp`): the embedded browser is detected safely before any JCEF class is touched (xygeni/xygeni-product-backlog#1688).
- Marketplace verifier no longer flags the deprecated `CredentialAttributes` constructor (xygeni/xygeni-product-backlog#1688).
- Scanner and MCP downloads now trust the certificates accepted in the IDE (Settings > Tools > Server Certificates) and honor the IDE HTTP proxy settings, so installation works behind intercepting proxies such as Zscaler where the JBR has no `mscapi` module (xygeni/tech-support#375).
- A failed scanner or MCP download is now reported as an installation error instead of logging "installed successfully" (xygeni/tech-support#375).
- A scan that finishes with findings (scanner exit code above 127, e.g. 134) is reported as successful instead of "finished with errors"; a scan where some types are not licensed (exit code 127) is also completed when the licensed types wrote their report, and the skipped types are named in the console (xygeni/xygeni-product-backlog#1976).
- A trailing slash in the API URL no longer breaks the scanner download and the IDE licence check: the URL is normalised when saved (xygeni/xygeni-product-backlog#1976).
- On IDEs without JCEF (e.g. Android Studio) the issue detail, its code flow graph and path, the AI explanation and the remediation actions are rendered with native Swing views instead of opening as raw HTML (xygeni/xygeni-product-backlog#1976).
- Installation failures now offer a "Retry installation" action, the console no longer claims the plugin was installed when any step failed, and uninstalling never follows a symlinked scanner folder (xygeni/xygeni-product-backlog#1976).
- Scan types the licence excludes (e.g. API Security) show "Not licensed" in their section instead of an empty node (xygeni/xygeni-product-backlog#1976).
- Missing report files before the first scan are no longer logged to the Xygeni console (xygeni/xygeni-product-backlog#1976).
- The remediation advice of API Security and AI Security findings is rendered as markdown (numbered steps, code spans) instead of one run-on line (xygeni/xygeni-product-backlog#1691, xygeni/xygeni-product-backlog#1692).
- API Security findings now open in the editor: the handler file and line are resolved from the report's API inventory (endpoint handler, `handler_file`, or the module's OpenAPI spec) since flaws carry no location of their own (xygeni/eclipse-plugin#22, xygeni/visual-studio-extension#15).
- An incremental scan (on save) reloads only the scan types it ran; API Security, AI, dependency, CI/CD and quality findings keep their last full-scan results instead of being re-read (xygeni/visual-studio-extension#15).

## [1.6.1] - 2026-07-27
### Added
- Code Quality scan: quality findings are listed in a new "Code Quality" section, with AI auto-fix support (xygeni/xygeni-product-backlog#56).

## [1.6.0] - 2026-06-04
### Added
- Xygeni IDE license validation: the plugin now registers your IDE seat against your Xygeni license on startup and enable free licences.
- Issue lists now show the line number next to each finding (e.g. `LoginValidator.java:52`), and each analysis section header shows its issue count (e.g. `SAST (3)`) so totals are visible even when the section is collapsed.

### Changed
- The Xygeni panel now reflects the connection state as soon as it opens: when the connection is valid the Configuration section collapses and the Scan section is shown expanded; when it's invalid the Configuration section is revealed so you can fix it.
- Running a manual scan now brings the Xygeni Console to the front so you can follow the output (the incremental scan-on-save no longer steals the panel).

### Fixed
- AI Explanation no longer fails with "Unmatched arguments" on Windows: the issue JSON is now passed to the scanner through a file (`--issue-json-file`) instead of inline on the command line, so embedded quotes in the code snippet don't break argument parsing (#410).

## [1.5.0] - 2026-05-21
### Added
- New MCP setup view to connect the Xygeni MCP server straight from the plugin, without leaving the IDE.
- New "Incremental scan" option in Settings and the Xygeni tool window, so you can choose whether scans look only at recent changes or sweep the whole project.

### Changed
- Saving a file now triggers a quick incremental scan, while the Run Scan button still launches a full project scan — so day-to-day editing stays fast and you can ask for the full picture whenever you want.

## [1.4.0] - 2026-03-03
### Added

- updated plugin icon by @lmrb-1968 in https://github.com/xygeni/intellij-plugin/pull/32
- Update scan command to exclude foo.xml files by @nico-car in https://github.com/xygeni/intellij-plugin/pull/33
- Feature/malware scan by @Carmendelope in https://github.com/xygeni/intellij-plugin/pull/38
- [Feature] 4073-automatic incremental by @Carmendelope in https://github.com/xygeni/intellij-plugin/pull/34
- [FEATURE] Code Flow by @Carmendelope in https://github.com/xygeni/intellij-plugin/pull/35

## [1.3.0] - 2026-02-20
### Added
- Support for AI Explanation for SAST vulnerabilities

## [1.2.0] - 2026-02-11
### Added
- Code Flow visualization for SAST vulnerabilities, featuring interactive Graph and Path views.
- Reload button in report headers to refresh security findings manually.

### Changed
- The "Run Scan" button now remains disabled until the Xygeni scanner is fully installed.


## [1.1.0] - 2026-01-29
### Fixed
- Fixed an issue where the Xygeni Server URL was not handled correctly 

### Added
- Added a setting to trigger a full project scan when saving a file

## [1.0.0] - 2025-12-05
### Added
- First public release of the IntelliJ plugin