// Runtime prelude: Scriptable-style globals implemented on top of the native bridge (__b).

if (typeof Promise === 'undefined') {
  var Promise = {
    all: function (a) { return a; },
    resolve: function (x) { return x; }
  };
}

function Request(url) {
  this.url = url;
  this.method = 'GET';
  this.headers = {};
  this.body = null;
  this.timeoutInterval = 20;
  this.response = null;
}
Request.prototype.loadString = function () {
  var r = __b.http(String(this.url), String(this.method), JSON.stringify(this.headers || {}),
    this.body == null ? null : String(this.body), Math.round(this.timeoutInterval * 1000));
  this.response = {
    statusCode: r.status,
    url: String(this.url),
    headers: JSON.parse(String(r.headersJson))
  };
  return String(r.body);
};
Request.prototype.loadJSON = function () {
  return JSON.parse(this.loadString());
};
Request.prototype.loadImage = function () {
  return __b.image(String(this.url));
};

var Device = {
  batteryLevel: function () { return __b.batteryLevel(); },
  isCharging: function () { return __b.isCharging(); },
  screenSize: function () { return new Size(__b.screenW(), __b.screenH()); },
  screenScale: function () { return __b.density(); },
  systemName: function () { return 'Android'; },
  systemVersion: function () { return String(__b.sdk()); },
  model: function () { return String(__b.model()); },
  name: function () { return String(__b.model()); },
  isUsingDarkAppearance: function () { return __b.isDark(); }
};

function DateFormatter() {
  this.dateFormat = null;
  this.locale = null;
  this._ds = null;
  this._ts = null;
}
[['No', null], ['Short', 'Short'], ['Medium', 'Medium'], ['Long', 'Long'], ['Full', 'Full']].forEach(function (p) {
  var label = p[0], value = p[1];
  DateFormatter.prototype['use' + label + 'DateStyle'] = function () {
    this._ds = value; this.dateFormat = null; this._dsSet = true;
  };
  DateFormatter.prototype['use' + label + 'TimeStyle'] = function () {
    this._ts = value; this.dateFormat = null; this._tsSet = true;
  };
});
DateFormatter.prototype.string = function (d) {
  var ds = this._ds, ts = this._ts;
  if (!this._dsSet && !this._tsSet) { ds = null; ts = null; }
  return String(__b.formatDate(d.getTime(), this.dateFormat, ds, ts, !!(this._dsSet || this._tsSet)));
};
DateFormatter.prototype.date = function (s) {
  var t = __b.parseDate(String(s), this.dateFormat);
  return isNaN(t) ? null : new Date(t);
};
