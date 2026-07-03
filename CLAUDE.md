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

## Coding Rules

These rules apply to all changes in this repository.

### General Approach
- Change the absolute minimal amount of code.
- When in doubt, ask questions.
- NEVER give "Rest of the code remains the same" or anything like it in an answer. It leads to code getting removed by accident, causing bugs.
- Look for comments that might instruct you about what not to do, or how removing the code below them might break something.

### Language and Dependencies
- This is an Android Java app. Do not introduce Kotlin code.
- Do not introduce Glide as a library.
- Try to use existing dependencies as much as possible, and avoid introducing new dependencies unless necessary.
- Be sure to include new imports at the top of the file when making changes. DO NOT reference a library directly by its long name in code — use imports.

### Comments
- Leave comments alone unless you are changing the code associated with them. Even then, don't make superfluous changes to comments.
- Leave the beginning of C-style comment blocks alone, and don't change their indentation (e.g. `/**`).

### Strings and XML
- When working with `strings.xml` files, do not modify any string resources that contain single quotes (either as apostrophes or escaped with backslash like `\'`) unless specifically requested. If a string with single quotes needs modification, ask for confirmation first. When adding new strings, follow the established patterns for escaping single quotes with backslashes where appropriate.
- Be sure to include new strings for `strings.xml` in the changes.

### Do Not Remove
- In `MainActivity.java`, DO NOT remove the lines containing `// Removing this will break Guest mode` or `Authentication.isLoggedIn = true;`.
- Do not remove code blocks that mention `oldSwipeMode`. It is an important feature.

### Formatting
- Clean up trailing whitespace for all lines.

### API Keys
- Read any new API keys through `app/src/main/java/me/edgan/redditslide/SecretConstants.java`.

### Performance
1. Minimize recomposition using proper keys
2. Use proper lazy loading with LazyColumn and LazyRow
3. Implement efficient image loading
4. Use proper state management to prevent unnecessary updates
5. Follow proper lifecycle awareness
6. Implement proper memory management
7. Use proper background processing

### Known Issues and Setup
- Reddit client ID required for API access - see setup documentation
- OAuth errors typically relate to incorrect redirect URI configuration
- Android System WebView version affects login functionality
- Backup/restore functionality available in Settings for user data migration