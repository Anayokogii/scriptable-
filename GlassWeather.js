// Glass Weather for Script Widgets (Android)
// Big tall translucent temperature + one line of text, on a transparent widget.
// Widget parameter: your city, e.g.  Mumbai   (default Kolkata)
// Data: Open-Meteo (free, no key).

var CITY = (args.widgetParameter || "Kolkata").trim() || "Kolkata";

var W = config.widgetWidth || (config.widgetFamily === "small" ? 155 : 329);
var H = config.widgetHeight || (config.widgetFamily === "large" ? 340 : 155);
var PAD = 6;
var AW = W - PAD * 2;
var AH = H - PAD * 2;

var DIGIT_H = 0.711;
var STRETCH = 1.8;

var FILL = new Color("#FFFFFF", 0.36);
var EDGE = new Color("#FFFFFF", 0.55);

function getWeather() {
  try {
    var geo = new Request(
      "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + encodeURIComponent(CITY)
    ).loadJSON();
    if (!geo.results || !geo.results.length) return null;
    var p = geo.results[0];
    var d = new Request(
      "https://api.open-meteo.com/v1/forecast?latitude=" + p.latitude + "&longitude=" + p.longitude +
      "&current=temperature_2m,weather_code&timezone=auto"
    ).loadJSON();
    return { city: p.name, temp: Math.round(d.current.temperature_2m), code: d.current.weather_code };
  } catch (e) {
    return null;
  }
}

function condition(c) {
  if (c === 0) return "Clear";
  if (c === 1) return "Mostly clear";
  if (c === 2) return "Partly cloudy";
  if (c === 3) return "Cloudy";
  if (c === 45 || c === 48) return "Fog";
  if (c >= 51 && c <= 57) return "Drizzle";
  if (c >= 61 && c <= 67) return "Rain";
  if (c >= 71 && c <= 77) return "Snow";
  if (c >= 80 && c <= 82) return "Showers";
  if (c === 85 || c === 86) return "Snow showers";
  if (c >= 95) return "Thunderstorm";
  return "Weather";
}

function digit(parent, ch, size, vs) {
  var t = parent.addText(ch);
  t.font = new Font("sans-serif-condensed", size);
  t.textColor = FILL;
  t.outlineColor = EDGE;
  t.outlineWidth = 1.4;
  t.verticalScale = vs;
  t.trimToDigits = true;
  t.lineLimit = 1;
  t.centerAlignText();
}

var wx = getWeather();
var temp = wx ? wx.temp + "°" : "--";
var label = wx ? condition(wx.code) + " • " + wx.city : "Weather unavailable";

var textSize = Math.max(11, Math.min(18, AH * 0.09));
var textH = textSize * 1.25;
var boxH = AH - textH - 10;

// size the temperature so it is boxH tall and fits the width
var widthEm = temp.length * 0.5;
var vs = STRETCH;
var size = boxH / (DIGIT_H * vs);
var maxSize = (AW - 12) / widthEm;
if (size > maxSize) {
  size = maxSize;
  vs = boxH / (DIGIT_H * size);
}
var gap = Math.max(4, Math.min((AW * 0.6 - size * widthEm) / Math.max(1, temp.length - 1), size * 0.6));

var w = new ListWidget();
w.backgroundColor = Color.clear();
w.setPadding(PAD, PAD, PAD, PAD);
w.centerAlignContent();
w.refreshAfterDate = new Date(Date.now() + 5 * 60 * 1000);

w.addSpacer();
var row = w.addStack();
row.layoutHorizontally();
row.centerAlignContent();
row.spacing = gap;
for (var i = 0; i < temp.length; i++) digit(row, temp.charAt(i), size, vs);

w.addSpacer(8);
var t = w.addText(label);
t.font = new Font("sans-serif-condensed-medium", textSize);
t.textColor = new Color("#FFFFFF", 0.75);
t.lineLimit = 1;
t.minimumScaleFactor = 0.6;
t.centerAlignText();
w.addSpacer();

Script.setWidget(w);
Script.complete();
