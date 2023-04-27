
# Media3 Android Project

Media3 is an Android project that showcases the capabilities of the Media3 API for playing and controlling media content. The project features various functionalities, including playing media content from a URL, controlling media playback, zooming in on videos, handling device orientation changes, displaying media in full-screen mode, and providing user feedback for media errors.

## Requirements

To run the Media3 project, you need to have Android Studio 4.0 or later installed on your computer. Additionally, you need an Android device running Android 7.1 or later, or an emulator with Android 7.1 or later.

## Getting Started

To get started with the Media3 project, follow these simple steps:

1.  Clone the project repository to your local machine.
2.  Open the project in Android Studio.
3.  Build and run the project on your Android device or emulator.

## Usage

The Media3 project's main activity provides a list of available videos for the user to select and play. Upon selection, the player activity provides a user interface with playback controls, including play, pause, seek, and stop, for controlling the media playback. The player activity also includes zooming capabilities and full-screen mode, and handles media errors by logging error messages to the logcat.

## Code Structure

The Media3 project comprises several classes, including:

-   `MainActivity`: This is the main activity of the project that handles user interaction and contains the project's user interface.
-   `PlayerActivity`: This class provides a custom view for displaying media content and includes playback controls.
-   `PlayerErrorMessageProvider`: This class provides user-friendly methods for handling media errors.
-   `PlayerEventListener`: This class retrieves all events emitted from the media player.
-   `ScaleGestureListener`: This class handles the pinch-to-zoom capabilities of the media player's playback controls.
- `player_controller.xml`: Provides custom controller to the player.

## Acknowledgments

The Media3 project was inspired by the official Android documentation on Media3 API. The project team would like to express their gratitude to the Android development community for their support and contributions.
