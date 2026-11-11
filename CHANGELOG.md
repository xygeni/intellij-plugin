<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-plugin Changelog
All notable changes to this project will be documented in this file.



## [1.6.3] - 2026-11-05

### Added
- Scanner options (xygeni/xygeni-product-backlog#415): a command-line-like field in Scan configuration adds `xygeni scan` options with
  autocompletion and validation (a closed list: include patterns, detectors, mute, standard, AI review,
  license analysis, custom configuration); the options the plugin sets itself (`--run`, `-o`, excludes…) are refused.
  Saved as `options` in `.xygeni/ide.json` and validated again when the file is read.
- `.xygeni/ide.json` is treated as input from the repository (xygeni/xygeni-product-backlog#415): an option or exclude with a quote or a control character is refused (on Windows it could add scanner options of its own), `--conf`, `--custom-detectors-dir` and `--mute` take a path inside the project (or `--mute=#profile`), the file is never written through a symbolic link, a save keeps the keys another plugin added, and "Restore defaults" rewrites the file instead of deleting it.
- Run one scan type alone: a play button in the header of each results section (SAST, SCA, Secrets…) runs just that
  scan and refreshes only its findings.
- Security (xygeni/xygeni-product-backlog#1438): a server URL that uses `http://` to a host other than localhost is
  refused, with the reason in the connection status, because the API token would travel in clear on every request.
  `http://localhost` stays allowed. The new "Allow insecure HTTP" setting lets a server without TLS through on purpose;
  the status then keeps warning that the token travels in clear.
- MCP server updates (xygeni/xygeni-product-backlog#2382): after an update the notification says what to do in plain
  words (restart your AI assistant so it uses the new version), with "Show me how" opening a per-assistant guide (Copilot,
  Claude Code, Cursor, Claude Desktop) in MCP Setup and a note in the Configuration panel that stays until dismissed with
  OK. The Configuration panel gains an "MCP server" line (installed version, update state, MCP Setup and Check for
  updates links) that says "Checking…" while the connection check runs. The MCP Setup guide gains the Java prerequisite,
  where the configuration goes for each assistant (`servers` vs `mcpServers`), a check-that-it-works step and troubleshooting.
- Scan configuration per project (xygeni/xygeni-product-backlog#415): a new SCAN CONFIGURATION panel in the Xygeni tool window chooses which scan types the full scan runs (one switch per scanner; SCA brings suspect dependencies) and which of the file-scoped ones (Secrets, SAST, IaC, Malware, Code Quality) also run on save, and which folders or glob patterns are excluded from every scan (passed as `--exclude-add`, so the scanner's default excludes are kept; needs scanner 6.23 or later). Saved in `.xygeni/ide.json` at the project root, shared with the other Xygeni IDE plugins and meant to be committed; the defaults (every scan type, Secrets / SAST / IaC / Malware on save, nothing excluded) write nothing. A full scan now shows only the findings of the scan types it ran.
- MCP server updates (xygeni/xygeni-product-backlog#2382): once a day, after the connection is validated, the plugin reads the published MCP server version and, according to the new "MCP server updates" setting (Settings > Tools > Xygeni), notifies with Update / Ignore this version actions (default), updates in the background, or does nothing. Tools > Xygeni > Check for MCP Server Updates checks now. The check is shared with the other Xygeni IDE plugins through `~/.xygeni/mcp/last-check.txt`; when the published version cannot be read (no network, not published yet) nothing is shown, it is only logged.
- The installed MCP server version is written to `~/.xygeni/mcp/version.txt` and shown with the path and the update status in the MCP Server Setup view (xygeni/xygeni-product-backlog#2382).
- The MCP server download is verified against the published SHA-256 checksum; a mismatch, or an update whose checksum cannot be read, keeps the JAR already installed. Until the checksum is published, a first install goes ahead with a visible warning. An update that cannot replace a JAR locked by a running MCP client (Windows) is kept as `.pending` and applied on the next IDE start (xygeni/xygeni-product-backlog#2382).
### Changed
- The scanner is installed in `~/.xygeni/plugins/xygeni/scanner/`, a folder that no longer carries the plugin version, so a plugin update no longer downloads the ~250 MB scanner again (the VS Code extension already worked this way). The newest complete scanner of the old `<version>` folders is copied there on the first start; those folders are left in place, since another JetBrains IDE still on an older plugin version runs its scanner from them.
- The tool window groups Connection, Scan and Scan configuration under a single CONFIGURATION panel (xygeni/xygeni-product-backlog#415).
- A 503 from the backend's `/ping` no longer marks the URL as invalid: the endpoint answers 503 while the server's CPU is above its threshold, so the plugin goes on with the token check instead of asking for a reconnect. When that token check itself gets a 5xx or no answer, the status says the server is unavailable and to retry, instead of "invalid token". The startup check also retries up to three times on other 5xx or a refused connection, and the CONFIGURATION panel shows "Connecting…" meanwhile instead of "Unknown".
- The MCP server is installed in `~/.xygeni/mcp/xygeni-mcp-server.jar`, shared with the other Xygeni IDE plugins, so its path no longer changes when the plugin is updated. An existing copy in `~/.xygeni/plugins/<plugin>/<version>/mcp` is replaced by the shared one and a notification asks to update the path in the MCP client configuration (xygeni/xygeni-product-backlog#2382).
- The MCP server JAR is written to a temporary file and renamed into place under a lock, so two IDEs installing at the same time never leave a half-written JAR (xygeni/xygeni-product-backlog#2382).
### Fixed
- A scanner download or extraction that stops halfway (a `lib` folder with a handful of jars, so not even the scanner's updater starts) is now reported as a failed installation with the Retry action, instead of "installed successfully".
- The Xygeni console view is disposed with its tool window content; the IDE no longer reports a memory leak for the plugin on shutdown.

## [1.6.2] - 2026-09-09
### Added
- API Security scan: API flaws are listed in a new "API Security" section, with endpoint, module, service, OWASP API Top 10 and CWE details (xygeni/xygeni-product-backlog#1691).
- AI Security scan: AI findings are listed in a new "AI Security" section, with the AI asset kind, the standards they map to (OWASP LLM / ASI Top 10) and their red-team vectors (xygeni/xygeni-product-backlog#1692).
- AI Security findings can be fixed with the Xygeni Agent (FIX IT tab, scanner `util rectify --ai`) (xygeni/xygeni-product-backlog#1692).
- API Security findings are listed by flaw type, like the other categories; the full title is shown in the details (xygeni/xygeni-product-backlog#1691).
- Scanner global options (xygeni/tech-support#378): "Skip SSL verification" (`--skip-ssl-verify`, for corporate proxies that inspect TLS traffic), "Skip scanner update" and "Verbose scanner output" checkboxes plus an "Additional global options" field in the Xygeni settings, placed before the scanner command. Skip SSL verification can be toggled from the CONFIGURATION panel, where the other options also show while they are on; a scanner call that fails on the SSL certificate offers to enable it.
- Settings (gear) button in the Xygeni tool window header (xygeni/tech-support#378).
### Fixed
- Startup no longer fails on IDEs without the JCEF module (`NoClassDefFoundError: com.intellij.ui.jcef.JBCefApp`): the embedded browser is detected safely before any JCEF class is touched (xygeni/xygeni-product-backlog#1688).
- Marketplace verifier no longer flags the deprecated `CredentialAttributes` constructor (xygeni/xygeni-product-backlog#1688).
- Scanner and MCP downloads now trust the certificates accepted in the IDE (Settings > Tools > Server Certificates) and honor the IDE HTTP proxy settings, so installation works behind intercepting proxies such as Zscaler where the JBR has no `mscapi` module (xygeni/tech-support#375).
- A failed scanner or MCP download is now reported as an installation error instead of logging "installed successfully" (xygeni/tech-support#375).
- A scan that finishes with findings (scanner exit code above 127, e.g. 134) is reported as successful instead of "finished with errors"; a scan where some types are not licensed (exit code 127) is also completed when the licensed types wrote their report, and the skipped types are named in the console (xygeni/xygeni-product-backlog#1976).
- A trailing slash in the API URL no longer breaks the scanner download and the IDE licence check: the URL is normalised when saved (xygeni/xygeni-product-backlog#1976).
- On IDEs without JCEF (e.g. Android Studio) the issue detail, its code flow graph and path, the AI explanation and the remediation actions are rendered with native Swing views instead of opening as raw HTML (xygeni/xygeni-product-backlog#1976).
- Installation failures now offer a "Retry installation" action, the console no longer claims the plugin was installed when any step failed, and uninstalling never follows a symlinked scanner folder (xygeni/xygeni-product-backlog#1976).
- On IDEs without JCEF the code flow graph is no longer squashed on narrow tool windows and can be zoomed (zoom in / out / fit / actual size, Ctrl/Cmd + wheel) and panned by dragging; the FIX IT tab and the other tab panes get the same margins and spacing as in the browser view (xygeni/xygeni-product-backlog#1976).
- On IDEs without JCEF the issue detail keeps its tabs (ISSUE DETAILS, CODE SNIPPET, CODE FLOW, FIX IT) instead of stacking every pane in one page (xygeni/xygeni-product-backlog#1976).
- On IDEs without JCEF the issue detail keys no longer break mid-word ("Explanat/ion"), the severity chip is coloured and separated from the title, and numbered remediation steps stay inside their column (xygeni/xygeni-product-backlog#1976).
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