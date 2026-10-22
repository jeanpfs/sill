// Viewport's pixel size and the capture scale the host takes from it. Sources/StreamProtocol/
// Viewport.swift needs StreamMessage.swift's byte helpers, so both compile with this file:
//
//   swiftc -O Sources/StreamProtocol/Viewport.swift Sources/StreamProtocol/StreamMessage.swift \
//     Tests/checks/viewport-pixels/main.swift -o .build/checks/viewport-pixels/check
//
// What it checks: pixelWidth and pixelHeight under their wire names; a viewport without them (an
// older client's) decodes with both nil and encodes without their keys; an older host's decoder
// (`BaseViewport`: Viewport as it was before the pixels, verbatim but for its name) reads a new
// viewport and skips them; and `captureScale(ceiling:pointsWidth:pointsHeight:)`: the panel's pixels
// per point when that is below the ceiling, the ceiling when it is above, never below 1×, Standard
// never raised, the limiting axis decides, and an older client or an empty source keeps the ceiling.
import Foundation

var failures = 0
func check(_ ok: Bool, _ what: String) {
    print("\(ok ? "ok" : "FAIL") \(what)")
    if !ok { failures += 1 }
}
func near(_ a: Double, _ b: Double) -> Bool { abs(a - b) < 1e-9 }

func encode<T: Encodable>(_ v: T) -> Data { try! JSONEncoder().encode(v) }
func decode<T: Decodable>(_ t: T.Type, _ d: Data) -> T? { try? JSONDecoder().decode(t, from: d) }
func keys(_ d: Data) -> Set<String> {
    Set(((try? JSONSerialization.jsonObject(with: d)) as? [String: Any] ?? [:]).keys)
}

/// Viewport as it was before the pixel size: what an older host decodes.
struct BaseViewport: Codable, Hashable {
    var width: Double
    var height: Double
    var scale: Double?
    var localCursor: Bool?
    var fps: Int?
}

// MARK: the wire

let withPixels = Viewport(width: 932, height: 648, scale: 1.0, localCursor: true, fps: 60, pixelWidth: 2448, pixelHeight: 1702)
check(keys(encode(withPixels)).isSuperset(of: ["pixelWidth", "pixelHeight"]), "the pixel size goes out as pixelWidth and pixelHeight")
check(decode(Viewport.self, encode(withPixels)) == withPixels, "a viewport with pixels round-trips")

let older = Viewport(width: 932, height: 648, scale: nil)
check(older.pixelWidth == nil && older.pixelHeight == nil, "a viewport made without pixels has none")
check(!keys(encode(older)).contains("pixelWidth") && !keys(encode(older)).contains("pixelHeight"), "and sends neither key")
let olderJSON = Data(#"{"width":932,"height":648,"fps":60}"#.utf8)
let fromOlder = decode(Viewport.self, olderJSON)
check(fromOlder != nil && fromOlder?.pixelWidth == nil && fromOlder?.pixelHeight == nil, "an older client's viewport decodes with no pixels")
let toOlderHost = decode(BaseViewport.self, encode(withPixels))
check(toOlderHost?.width == 932 && toOlderHost?.height == 648 && toOlderHost?.fps == 60, "an older host reads a viewport that carries pixels")

// MARK: the rule

func scale(_ v: Viewport, ceiling: Double = 2, _ w: Double, _ h: Double) -> Double {
    v.captureScale(ceiling: ceiling, pointsWidth: w, pointsHeight: h)
}
let fold = Viewport(width: 932, height: 648, scale: nil, pixelWidth: 2418, pixelHeight: 1752)

// The Desktop, 1512x982 points, on a 2418 px wide panel: 1.599 px per point, below Retina.
check(near(scale(fold, 1512, 982), 1.599), "a Desktop wider than the panel's pixels per point is captured at the panel's own scale")
// A window an Aa-fit made as many points as the panel has dp: 2.59 px per point, above Retina.
check(near(scale(fold, 932, 648), 2), "a source the panel shows above Retina is captured at the ceiling")
check(near(scale(fold, ceiling: 1, 1512, 982), 1), "Standard is never raised")
check(near(scale(fold, ceiling: 1, 400, 300), 1), "Standard stays 1 for a small source")
// Fewer pixels than points: never below 1x.
let tiny = Viewport(width: 300, height: 200, scale: nil, pixelWidth: 600, pixelHeight: 400)
check(near(scale(tiny, 1512, 982), 1), "a panel with fewer pixels than the source has points still gets 1×")
// The tighter axis decides: a tall window in a wide panel is limited by height.
check(near(scale(fold, 500, 1500), 1.168), "the limiting axis decides (1752 px over 1500 points)")
// Rounded down: never more pixels than the panel.
let odd = Viewport(width: 1, height: 1, scale: nil, pixelWidth: 1000, pixelHeight: 1000)
check(near(scale(odd, ceiling: 2, 700, 700), 1.428), "rounded down to 1/1000 (1000/700 is 1.42857), so the frame fits the panel")
let framePixels = (700 * scale(odd, 700, 700)).rounded(.down)
check(framePixels <= 1000, "and the captured width never exceeds the panel's pixels")
// An older client, an empty source and a half-reported size keep the ceiling.
check(near(scale(older, 1512, 982), 2) && near(scale(older, ceiling: 1, 1512, 982), 1), "no pixel size keeps the configured scale")
check(near(scale(fold, 0, 982), 2) && near(scale(fold, 1512, 0), 2), "an empty source keeps the ceiling")
check(near(scale(Viewport(width: 1, height: 1, scale: nil, pixelWidth: 2418, pixelHeight: nil), 1512, 982), 2), "a half-reported pixel size is ignored")
check(near(scale(Viewport(width: 1, height: 1, scale: nil, pixelWidth: 0, pixelHeight: 0), 1512, 982), 2), "a zero pixel size is ignored")

print(failures == 0 ? "all checks passed" : "\(failures) FAILED")
exit(failures == 0 ? 0 : 1)
