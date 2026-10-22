import ApplicationServices
import Network
import StreamProtocol

/// The text focus, the host's side (kind 33; TextFocus.swift). A device that subscribes is sent whether a
/// text field has focus on the Mac: the state as it stands at once, then every change. The rules are
/// TextFocusTracker's; this reads Accessibility and keeps which connections subscribed.
///
/// While at least one connection subscribes, the Mac's focus is read four times a second, each read
/// awaited before the next is scheduled, so reads never overlap. With no subscriber nothing is read.
@MainActor
final class TextFocusWatcher {
    /// How often the Mac's focus is read while a device subscribes.
    static let interval = 0.25

    private let server: StreamServer
    /// The Accessibility reader; nil on a synthetic host, which never reads the Mac's focus and reports
    /// it unfocused.
    private let focus: AccessibilityFocus?
    private var tracker = TextFocusTracker<ObjectIdentifier>()
    private var connections: [ObjectIdentifier: NWConnection] = [:]
    private var look: Task<Void, Never>?

    init(server: StreamServer, focus: AccessibilityFocus?) {
        self.server = server
        self.focus = focus
    }

    /// A kind 33 from `connection`: `{"stop":true}` ends its subscription, anything else subscribes.
    func received(_ payload: Data, from connection: NWConnection) {
        let id = ObjectIdentifier(connection)
        if TextFocus.isStop(payload) {
            drop(id)
            return
        }
        guard tracker.subscribe(id) else { return }
        connections[id] = connection
        startLooking()
    }

    /// The device left: forgotten without a word.
    func clientLeft(_ connection: NWConnection) {
        drop(ObjectIdentifier(connection))
    }

    private func drop(_ id: ObjectIdentifier) {
        guard tracker.unsubscribe(id) else { return }
        connections[id] = nil
        if tracker.subscribers.isEmpty { stopLooking() }
    }

    private func startLooking() {
        guard look == nil else { return }
        look = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                let state = await self.readState()
                guard !Task.isCancelled else { return }
                self.observed(state)
                try? await Task.sleep(for: .seconds(TextFocusWatcher.interval))
            }
        }
    }

    private func stopLooking() {
        look?.cancel()
        look = nil
        focus?.release()
    }

    /// The Mac's focus, or nil when it could not be read (an app that does not answer): then nothing is
    /// sent and the last state stands.
    private func readState() async -> TextFocus? {
        guard let focus else { return .unfocused }
        return await focus.read()
    }

    private func observed(_ state: TextFocus?) {
        guard let state else { return }
        let sends = tracker.observe(state)
        guard !sends.isEmpty else { return }
        let payload = Wire.encode(state)
        let message = StreamMessage(kind: .textFocus, timestamp: Date().timeIntervalSince1970, isKeyframe: false, payload: payload)
        server.send(each: sends.compactMap { send in connections[send.id].map { ($0, message) } })
    }
}

/// Reads the Mac's focused element through Accessibility, on its own serial queue, so a slow app never
/// stalls the host. It turns on `AXManualAccessibility` for the frontmost app only (Chromium and Electron
/// expose their web content only when an assistive client asks), and turns it off again on `release`.
final class AccessibilityFocus: @unchecked Sendable {
    /// Each AX call waits at most this long for an app: a hung app reads as unreadable, not as a stall.
    private static let timeout: Float = 0.2

    private let queue = DispatchQueue(label: "sill.text-focus", qos: .userInitiated)
    /// The apps this reader turned accessibility on for; on `queue`.
    private var enabled: Set<pid_t> = []

    /// The Mac's focus now, or nil when it could not be read. On `queue`, one read at a time.
    func read() async -> TextFocus? {
        await withCheckedContinuation { done in
            queue.async { done.resume(returning: self.readNow()) }
        }
    }

    /// Turns accessibility off again for every app it was turned on for. Nothing is read meanwhile.
    func release() {
        queue.async {
            for pid in self.enabled {
                AXUIElementSetAttributeValue(AXUIElementCreateApplication(pid), "AXManualAccessibility" as CFString, kCFBooleanFalse)
            }
            self.enabled = []
        }
    }

    private func readNow() -> TextFocus? {
        let system = AXUIElementCreateSystemWide()
        AXUIElementSetMessagingTimeout(system, Self.timeout)
        let app = attribute(system, "AXFocusedApplication")
        guard app.error == .success, let value = app.value else { return nil }
        let application = value as! AXUIElement
        AXUIElementSetMessagingTimeout(application, Self.timeout)
        var pid: pid_t = 0
        guard AXUIElementGetPid(application, &pid) == .success else { return nil }
        if !enabled.contains(pid) {
            AXUIElementSetAttributeValue(application, "AXManualAccessibility" as CFString, kCFBooleanTrue)
            enabled.insert(pid)
        }
        let focused = attribute(system, "AXFocusedUIElement")
        switch focused.error {
        case .success:
            guard let value = focused.value else { return .unfocused }
            let element = value as! AXUIElement
            AXUIElementSetMessagingTimeout(element, Self.timeout)
            return TextFocus.classify(role: attribute(element, "AXRole").value as? String,
                                      subrole: attribute(element, "AXSubrole").value as? String,
                                      editable: attribute(element, "AXEditable").value as? Bool)
        case .noValue:
            return .unfocused
        default:
            return nil
        }
    }

    private func attribute(_ element: AXUIElement, _ name: String) -> (value: CFTypeRef?, error: AXError) {
        var value: CFTypeRef?
        let error = AXUIElementCopyAttributeValue(element, name as CFString, &value)
        return (value, error)
    }
}
