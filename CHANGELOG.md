# Changelog

## [Unreleased]

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
