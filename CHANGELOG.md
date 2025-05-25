# CHANGELOG

The old changelog can be read in the [CHANGELOG.md](https://github.com/Haptic-Apps/Slide/blob/master/CHANGELOG.md).

---

7.3.2 / 2025-5-24
============
* Fixed the cascade effect while using oldSwipeMode
* Add playback speed control to video player
* Inconsistent tap behavior on comments with media
* Fixed an intermittent display issue with Selftext preview images
* Tumblr gif fixes

7.3.1 / 2025-5-9
===========
* Dismiss toolbar search and keyboard on suggestion selection
* Prioritize Search icon, move Shadowbox to overflow
* Fixed an intermittent display issue with Selftext preview images
* Fixed earch dialog scales weirdly from FAB  #215
* Fixed Tapping on "Moderation" does nothing. And I can't hide it either from drawer items #111
* Added Hide subscribed subreddit tabs setting in Settings | General
* Fixed NullPointerException on resume in MediaView (ExoPlayer) #178
* Fixed many different crashes

7.3.0 / 2025-4-24
============
* Fixed Downloaded images are always saved as <subreddit>/download.jpeg #173
* Fixed issue with badly formatted code blocks
* Fixed Brief flash when opening videos/GIFs #162
* Fixed Controls briefly reappear when tapping to hide them #121
* Fixed Controls briefly appear when opening a video #119
* Fixed Pinch-to-zoom causes jumping in video player #168
* Added support for inputting the client ID via a QR code
* Fixed opening redd.it links inside and outside Slide #172

7.2.9 / 2025-4-3
===========
* Fixed Some video downloads are corrupted #169

7.2.8 / 2025-4-3
================
* Fix for crash related to "Pause video instead of ducking"
* Fixed Add button for subreddits in "Manage your subreddits" is greyed out #167

7.2.7 / 2025-3-28
=================
* Fixed bug with old swipe mode and vertical mode Reddit galleries #165 #166
* Fixed Can't create multireddit tabs #142
* Fixed YouTube links open in browser instead of YouTube app when using custom tabs #164
* Properly themed all dialog boxes in Settings | Manage your subreddits
* Added setting to put colored border around dialog boxes. Currently limited to Settings | Manage your subreddits, and not the default.
* Truncated CHANGELOG.md to post fork, but added link to the old CHANGELOG.md

7.2.6 / 2025-3-26
=================
* Lots of improvements to Imgur albums and Reddit galleries
* Fixed /s/ style links #163
* Fixed preview image display for Reddit videos when there is no preview
* Fixed Posts with preview.redd.it urls and highlighted text using backticks display the images/urls wrong #161
* Fixed crash from going back from a video
* Ducking, lowering volume, audio play when it is interrupted by default
* Setting to pause instead of duck in general settings
* Fixed Crosspost has a blank preview when media list is empty #148
* Fixed how Subreddit sync writes the urls for multireddits #143
* Fixed Crash in SubmissionsView: NullPointerException on Click #144

7.2.5 / 2025-3-21
=================
* Fixed Slide doesn't request notification permission on first launch #146
* Disabling converting preview links into images when Show content type text beside links is enabled #159
* Fixed all storage location checks to also check for storage access
* Added longclick option to unset storage location
* Implemented same crash fix from #147 for Imgur and Tumblr
* Fixed Crash in crosspost when media list is empty #147
* Reverted change to preview images which fixes many cases

7.2.4 / 2025-3-15
=================
* Fixed "Open content" option doesn’t support Reddit Galleries #115
* Fixed Sorting in User Profiles #139
* Always fully collapse the sticky comment
* Added link to the Client ID instructions the dialog in General settings
* Allow user to unblock another user #126
* Made Peek content and No longclicks on preview images toggle each other
* Fixed an ANR when reloading subs #134
* Fixed crashes in MultiredditOverview.java #133 #141

7.2.3 / 2025-3-9
================
* Added a restore form file button to the Tutorial #132
* Added link to the Client ID instructions #106
* Added a background the the client id dialog in the tutorial #106
* Fixed Peek content on Reddit galleries opens Reddit website #123
* Removed peeking in comments
* Fixed Reddit GIF loading with peek #124
* Fixed missing video audio when saving files #122

7.2.2 / 2025-3-2
================
* Two more fixes for subreddit filters

7.2.1 / 2025-2-28
=================
* Fixed multiple free_emote_pack/snoomoji emoticons in the same comment
* Removed the Notifications dialog in MainActivity.java
* Disabled auto-offline
* Fixed the size of the Subreddit content filters dialog

7.2.0 / 2025-2-26
=================
* Fixed both regular notifications and piggyback notifications #80
* Fixed subreddit filters
* Fixed previews for Image and Reddit Video content types

7.1.9 / 2025-2-25
================
* Subreddit content filters dialog box enhancements
* Added Subreddit content filters till restart setting in Filters settings #109
* Made the Subreddit content filters till restart setting the default

7.1.8 / 2025-2-24
=================
* Made the video background black instead of transparent
* Changed the default to muted video
* Improved the mute and unmute icons
* Added setting in General settings to allow unmuted video by default
* Fixed gallery preview and thumbnail images
* Made Go to profile and Go to multis single line
* Fixed multireddit FAB search #101
* Another attempt at fixing Title text is sometimes cut off #11
* Implemented a new content filter toggle button functionality
* Added NSFW Tumblrs and NSFW Videos to the content filters #76
* Fixed LINK preview images that were appearing and disappearing
* Fixed Go to subreddit says subreddit not found for subscribed subreddits #108

7.1.7 / 2025-2-21
=================
* Added the Reddit Client ID override to the tutorial
* Fixed Crash related to reordering subreddits #105
* Fixed Crash related to multireddit overview #104
* Fixed Crash related to gif playback with null checks #103

7.1.6 / 2025-2-20
=================
* Fixed Multireddit longclick button text #100
* Fixed NSFW Content Visible Despite Being Turned Off #77
* Fixed Posts with Reddit links show empty previews #92
* Fixed Base Theme issue when swapping accounts #99
* Fixed search boxes to be single input line
* Removed Select storage location option #90
* Updated minSdk from 21(Android 5.0) to 29(Android 10)

7.1.5 / 2025-2-15
=================
* Fixed It creates a subfolder for each downloaded image/GIF/video #83
* Fixed Crossposts don't follow "Picture mode" setting in Post layout settings #86
* Fixed You button in the Navigation bar #87
* Fixed Crash on "Open Externally" in Vertical Gallery Mode #89
* Fixed Galleries won't download in vertical scroll mode #88
* Fix for Open externally for the gallery in the Reddit Gallery Pager
* Fixed image download notification crash
* Made the behavior of themes more consistent
* Fixed Slide crashes when opening a post with a large image preview #79
* Added notification permission to AndroidManifest.xml #80
* Fixed Content Settings Cause App Crash & Loading Issues #74

7.1.2 / 2025-2-10
=================
* Removing *.redd.it for Open by default
* Removed checkClipboard to fix #72
* Fixed "Crash when scrolling through image posts" with more null checks #73
* Fixed Slide crashes when trying to access subreddits #69
* Fixed Read Later crash #62
* Added support for i.redd.it links for inlined preview images
* Converted giphy emotes to inlined preview images style #38
* Themed Client ID override dialog
* Fixed FAB multi-choice #66
* Fixed separators, spacing, and padding for general settings #66
* Added separator between emote animation and longclick settings #66

7.1.1 / 2025-2-8
================
* Fixed crash when switching back from Guest mode #68
* Themed exit dialog
* Themed profile dialog

7.1.0 / 2025-2-8
================
* Added multireddit search for the FAB search button #63
* Made the search button at the top work for multireddit tabs #63
* Converted subreddit content filter dialogs to use the theme

7.0.9 / 2025-2-6
================
* Fixed crash in UserSubscriptions.java
* Converted preview reddit links to inline images #40
* Reversed the Subreddit content filter logic #55


7.0.8 / 2025-2-5
================
* Fixed sorting for multireddit tabs #44
* Fixed crash in AlbumPager.java
* Fixed comment emotes advance when you leave Slide and come back #56
* Fixed preview images for crossposts of Reddit videos #57


7.0.7 / 2025-2-3
================
* Fixed ability to download Reddit videos #49
* Fixed image picking crash #53
* Fixed ANR related to opening a Reddit link #52
* Fixed crash related to viewing a user's profile and gold #51
* Fixed crash related to blocking a subreddit #50
* Fixed Imgur for the various types #42
* Show galleries as images instead of thumbnails in Shadowbox mode #41
* Improved the strings related to no longclicks #48
* Fixed web browser selection in Settings | Link handling
* Made custom tabs setting use the selected web browser
* Improved RedGifs where more should have sound

7.0.6 / 2025-2-1
=================
* Added Old Swipe mode in General settings

* Improved support for RedGifs

7.0.5 / 2025-1-28
=================
* Restored BlankFragment for 3-button navigation

7.0.4 / 2025-1-27
=================
* Added a link to the privacy policy in the app

7.0.3 / 2025-1-26
=================
* Made PREF_IGNORE_SUB_SETTINGS default to true

7.0.2 / 2025-1-25
=================
* Fixed frontpage sort inconsistency #39
* Added Frontpage sort setting #39
* Fixed media controls by reverting the upgrade to media3

7.0.1 / 2025-1-24
=================
* Fixed crashes caused by duplicate gif emotes in comments #37
* Changed Reddit Client ID override in General settings to restart the app
* Updated from exoplayer to media3

7.0.0 / 2025-1-24
=================
* Fixed Google drive backup and restore #30
* Fixed local file backup and restore #30
* Moved client id to the top of General settings
* Renamed Save image location to Save storage location in General settings

6.9.9 / 2025-1-21
=================
Fixed share image #36
Fixed shortened URLs to work with the official Reddit app #35

6.9.8 / 2025-1-20
=================
* Fixed Multireddits button in the navigation bar #34

6.9.7 / 2025-1-18
=================
* Fixed comments screen swipe left behavior #32
* Added more options for filtering subreddit content
* Fixed gallery preview image size in comments #20

6.9.6 / 2025-1-16
=================
* Fixed comments screen #25

6.9.5 / 2025-1-16
=================
* Removed BlankFragment everywhere to fix cascade effect #22
* Fixed saving media by updating to SAF #21
* Fixed single image Imgur albums #24

6.9.3 / 2025-1-3
================
Tried android:autoVerify="false"

6.9.2 / 2025-1-1
================
* Added another fix for cutoff text in post titles

6.9.1 / 2024-12-31
==================

6.9.0 / 2024-12-31
==================
* Reverting the Open by default change

6.8.8 / 2024-12-26
==================
* Fixed bug with animated gif playback in non-galleries

6.8.7 / 2024-12-25
==================
* Removed all reference to the Donate button in Settings

6.8.6 / 2024-12-24
==================
* Fixed Open externally crash

6.8.5 / 2024-12-24
==================
* Fixed SELECT PICTURE button to only require one press
* Improved defaults
* Removed Android Beam(NFC) support
* Removed Open by default links

6.8.2 / 2024-12-24
==================
* Removed open with links

6.7.13 / 2024-12-22
===================
* Changed applicationId, minSdk, and versionCode for Google Play Store

6.7.12 / 2024-12-22
==================
* Fixed gif post issue #17
* Fixed a mix of animated and non-animated images in Reddit galleries #4

6.7.11 / 2024-12-20
===================
* Fixed gallery preview showing the last image instead of the first #16
* Fixed galleries being misdetected if the url wasn't a gallery url #15

6.7.10 / 2024-12-19
===================
* Fixed consistently of emotes in comments respecting the animation toggle
* Improved giphy gifs using the preview if it is available
* Fixed galleries to not show a preview where mediaInfo has all failed entries

6.7.9 / 2024-12-19
==================
* Fixed emotes with text before and after the gif
* Fixed the emote scaling
* Fixed the emote animation setting to be consistent
* Replaced all references to /r/slideforreddit with /r/slidereddit

6.7.8 / 2024-12-19
==================
* Added setting to disable longclicks on preview images

6.7.7 / 2024-12-18
==================
* Fixed gallery image previews to be consistent on scroll down and back

6.7.6 / 2024-12-15
==================
* Fixed crash in RedditGalleryView
* Changed layout/submission_largecard_middle.xml to be Linear instead of Relative to attempt to fix a cut off bug

6.7.5 / 2024-12-14
==================
* Removed Pro mode, but not any of the features
* Added support for giphy gifs in Reddit comments
* Added preview image for crossposted galleries
* Improved the consistency of gallery image display

6.7.4 / 2024-12-12
==================
* Added preview images for Reddit galleries
* Improved the consistency of displaying images in Reddit galleries in the default view

6.7.2 / 2024-12-11
==================
* Fixed multi-reddit tabs
* Added Reddit default emote support to comments

6.7.1.1 / 2024-12-09
==================
* Added a dynamic Client ID feature
* Added shared links fix
