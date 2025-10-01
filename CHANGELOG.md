# Changelog

## [Unreleased]

## [5.4.0]
### Changed
- Client version updated on [5.4.3](https://github.com/reportportal/client-java/releases/tag/5.4.3), by @HardNorth
- Replace "jsr305" annotations with "jakarta.annotation-api", by @HardNorth
- Switch on use of `Instant` class instead of `Date` to get more timestamp precision, by @HardNorth
### Removed
- Java 8-10 support, by @HardNorth

## [5.3.2]
### Changed
- Client version updated on [5.3.17](https://github.com/reportportal/client-java/releases/tag/5.3.17), by @HardNorth

## [5.3.1]
### Changed
- Client version updated on [5.3.16](https://github.com/reportportal/client-java/releases/tag/5.3.16), by @HardNorth

## [5.3.0]
### Changed
- Client version updated on [5.3.14](https://github.com/reportportal/client-java/releases/tag/5.3.14), by @HardNorth

## [5.2.4]
### Added
- Putting last error logs of tests to Items' description, by @YevhenPiskun
### Changed
- Client version updated on [5.2.25](https://github.com/reportportal/client-java/releases/tag/5.2.25), by @HardNorth

## [5.2.3]
### Changed
- Client version updated on [5.2.13](https://github.com/reportportal/client-java/releases/tag/5.2.13), by @HardNorth

## [5.2.2]
### Changed
- Client version updated on [5.2.11](https://github.com/reportportal/client-java/releases/tag/5.2.11), by @HardNorth
- Spock dependency marked as `compileOnly` to force users specify their own versions, by @HardNorth

## [5.2.1]
### Changed
- Client version updated on [5.2.4](https://github.com/reportportal/client-java/releases/tag/5.2.4), by @HardNorth
### Removed
- `commons-model` dependency to rely on `clinet-java` exclusions in security fixes, by @HardNorth

## [5.2.0]
### Changed
- Client version updated on [5.2.1](https://github.com/reportportal/client-java/releases/tag/5.2.1), by @HardNorth
- Spock dependency marked as `implementation` to force users specify their own versions, by @HardNorth
### Removed
- Unused code, by @HardNorth

## [5.1.4]
### Changed
- Client version updated on [5.1.24](https://github.com/reportportal/client-java/releases/tag/5.1.24), by @HardNorth
- `ReportPortalSpockListener.logError` now sends exceptions directly to RP bypassing logging to reduce amount of user misconfigurations, by @HardNorth

## [5.1.3]
### Changed
- Client version updated on [5.1.22](https://github.com/reportportal/client-java/releases/tag/5.1.22), by @HardNorth

## [5.1.2]
### Fixed
- Display name for iterations, by @AlexeyAkentyev
- Inherited spec sent parent's test steps to the parent spec instead of inherited spec, by @AlexeyAkentyev
### Changed
- Client version updated on [5.1.17](https://github.com/reportportal/client-java/releases/tag/5.1.17), by @HardNorth

## [5.1.1]
### Changed
- Client version updated on [5.1.16](https://github.com/reportportal/client-java/releases/tag/5.1.16), by @HardNorth
- `NodeInfoUtils` class refactored to not use synchronized Map, by @HardNorth 

## [5.1.0]
### Added
- Spock 2.0 compatibility, by @mihalichzh
### Removed
- `finally` keyword, see [JEP 421](https://openjdk.java.net/jeps/421), by @HardNorth
### Changed
- Client version updated on [5.1.11](https://github.com/reportportal/client-java/releases/tag/5.1.11), by @HardNorth

## [5.1.0-RC-6]
### Added
- System attribute reporting, by @HardNorth
### Changed
- Client version updated on [5.1.10](https://github.com/reportportal/client-java/releases/tag/5.1.10), by @HardNorth

## [5.1.0-RC-5]
### Added
- Test Case ID templating, by @HardNorth
### Changed
- Client version updated on [5.1.9](https://github.com/reportportal/client-java/releases/tag/5.1.9), by @HardNorth
- Slf4j version updated on 1.7.36, by @HardNorth

## [5.1.0-RC-4]
### Changed
- Client version updated on [5.1.0](https://github.com/reportportal/client-java/releases/tag/5.1.0)

## [5.1.0-RC-3]
### Added
- Feature / Specification / Iteration / Fixture start methods which are overridable
- JSR-305 annotations
- `buildFinishTestItemRq` overridable method
### Changed
- Client version updated on [5.1.0-RC-12](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-12)
### Fixed
- [Issue #26](https://github.com/reportportal/agent-java-spock/issues/26): NullPointerException when data value has null value

## [5.1.0-RC-2]
### Added
- `Attributes` annotation handling for Specs and Fixtures

## [5.1.0-RC-1]
### Added
- Iterations as nested steps reporting
- Parameters support
- Ignore fixture functionality support
### Fixed
- Iterations now fail parent feature
- Unrolled step description
- Fixture reporting

## [5.1.0-BETA-3]
### Added
- Code reference support
- TestCaseID support
### Changed
- Refactoring of the agent's structure 

## [5.1.0-BETA-1]
### Changed
- Client version updated on [5.1.0-RC-6](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-6)
- Version changed on 5.1.0

## [3.0.0]
