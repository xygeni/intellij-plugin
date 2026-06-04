<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-plugin Changelog
All notable changes to this project will be documented in this file.



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