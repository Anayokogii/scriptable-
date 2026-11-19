# Script Widgets (Scriptable-style widgets for Android)

Write JavaScript with the Scriptable widget API, get a home-screen widget.

## Build
1. Open this folder in Android Studio (Koala or newer). It creates the Gradle wrapper and syncs.
2. Run the `app` configuration on a device/emulator (Android 8.0+).
3. Home screen: long-press > Widgets > Script Widgets. Pick a script when asked.

Note: this project was written without being compiled. Expect to fix a few small compile errors on first build.

## How it works
- Scripts run in Rhino (interpreter mode). `await`/`async` are stripped, so network calls are synchronous.
- The script builds a `ListWidget` tree; a Canvas renderer lays it out at iOS point sizes
  (small 155pt, medium/large 329pt wide) and scales to the real widget size.
- The result is shown as a bitmap in the widget. Tap = refresh (or open `widget.url`).

## Supported API
ListWidget, WidgetStack, WidgetText, WidgetDate, WidgetImage, WidgetSpacer (layout, padding, spacing,
alignment, corner radius, border, size, background color/gradient/image, fonts, shadow, lineLimit,
minimumScaleFactor), Color, Font, Point, Size, LinearGradient, Request (loadString/loadJSON/loadImage),
DateFormatter, Device (battery, screen, dark mode), args.widgetParameter, config.widgetFamily,
Script.setWidget/complete, console.log, refreshAfterDate.

## Known limitations
- No SF Symbols, FileManager, Keychain, Location, Notification, Photos, importModule.
- Rhino ~ES6: no optional chaining (`?.`), no `??`, no real Promises/async.
- Dates/timers are drawn once per refresh (no live ticking).
- Only widget-level `url` is tappable (not per-element).
- Android refreshes widgets at most every ~30 min (plus refreshAfterDate alarms, min 5 min, and tap).
