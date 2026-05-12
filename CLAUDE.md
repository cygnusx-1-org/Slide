# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Development Commands

### Building
- `./gradlew build` - Assemble and test the project
- `./gradlew assembleDebug` - Build debug APK
- `./gradlew assembleRelease` - Build release APK (requires keystore.properties)
- `./gradlew bundleWithGPlayRelease` - Build AAB bundle for Google Play Store
- `./gradlew installWithGPlayDebug` - Build and install debug APK to connected device

### Testing
- `./gradlew test` - Run all unit tests
- `./gradlew testNoGPlayDebugUnitTest` - Run unit tests for non-Google Play debug variant
- `./gradlew connectedAndroidTest` - Run instrumentation tests on connected devices
- `./gradlew check` - Run all checks including tests and lint

### Code Quality
- `./gradlew spotlessCheck` - Check code formatting
- `./gradlew spotlessApply` - Apply code formatting fixes
- `./gradlew lint` - Run Android lint checks
- `./gradlew lintFix` - Run lint and apply safe suggestions

### Recommended Pre-commit Workflow
1. `./gradlew spotlessApply` - Format code
2. `./gradlew test` - Run unit tests
3. `./gradlew lint` - Check for code issues

## Project Architecture

### Core Structure
This is an Android Reddit client app built around the JRAW (Java Reddit API Wrapper) library. The architecture follows a modified MVP pattern with clear separation of concerns:

**Main Packages:**
- `Activities/` - UI controllers including MainActivity, CommentsScreen, MediaView
- `Adapters/` - RecyclerView adapters for posts, comments, and data management
- `Fragments/` - UI fragments for different content views
- `SubmissionViews/` - Specialized views for different Reddit post types

### Key Architectural Patterns
- **Repository Pattern**: Classes like `UserSubscriptions`, `Authentication`, `HasSeen` manage data
- **Controller Pattern**: Dedicated controllers for drawer, search, sidebar functionality
- **Offline-First Design**: Extensive caching with graceful degradation
- **Modular Content Handling**: Different viewers for images, videos, albums, text posts

### Reddit API Integration
- **JRAW Library**: Handles all Reddit API communication and OAuth authentication
- **Data Flow**: Authentication → SubredditPosts → Adapters → UI
- **Caching**: Local storage for posts, images, and user preferences
- **Background Processing**: AsyncTasks for non-blocking API calls

### Build Variants
The app has two product flavors:
- `withGPlay` - Google Play Store version with Google services
- `noGPlay` - F-Droid version without Google dependencies

### Content Types and Media Handling
- `ContentType.java` determines media type from URLs
- Specialized activities for different content (images, videos, albums, web links)
- ExoPlayer integration for video playback
- Universal Image Loader for image caching and display

### Theming System
- Dynamic theming with per-subreddit color customization
- Multiple theme variants (dark, light, AMOLED)
- `ColorPreferences` and `Palette.java` manage theming

## Important Configuration Files

### Build Configuration
- `app/build.gradle` - Main build configuration with dependencies and variants
- `keystore.properties` - Signing configuration (not committed to git)
- `gradle.properties` - Gradle JVM settings and Android configuration
- `proguard-rules.pro` - Code obfuscation rules for release builds

### Reddit Integration
- `app/src/main/assets/secretconstants.properties` - Reddit client configuration
- Must be configured with valid Reddit client ID for API access
- See `/docs/SETUP.md` for Reddit client setup instructions

### Code Formatting
Spotless configuration in `app/build.gradle`:
- Java: removes unused imports, trims whitespace, 4-space indentation
- XML: 4-space indentation, trims whitespace
- Misc files (gradle, markdown): consistent formatting

### Testing Framework
- **Unit Tests**: JUnit 4 with Mockito, Robolectric, PowerMock
- **Test Location**: `app/src/test/java/me/edgan/redditslide/test/`
- **Lint Configuration**: Custom rules in build.gradle disable translation checks and other specific warnings
- **Instrumentation Tests**: Android tests for UI and integration testing

## Development Notes

### Media Rotation Feature
Recent commits show work on media rotation functionality in `MediaView.java`. This includes support for rotating images, GIFs, and non-preview media with improvements to the user interface.

### Code Style
- Follow existing Java conventions in the codebase
- Use 4-space indentation (enforced by Spotless)
- Maintain consistency with existing patterns for Activities, Adapters, and Fragments
- The `.cursor/rules/c_style_comments.mdc` rule preserves C-style comment formatting

### Known Issues and Setup
- Reddit client ID required for API access - see setup documentation
- OAuth errors typically relate to incorrect redirect URI configuration
- Android System WebView version affects login functionality
- Backup/restore functionality available in Settings for user data migration