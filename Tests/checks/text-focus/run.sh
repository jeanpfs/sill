#!/bin/bash
# The text focus (kind 33; docs/clipboard-plan.md's convention): Sources/StreamProtocol compiled as one
# module with the check. The kind and TextFocus's JSON, the window list's `textFocus`, the stop payload,
# the classifier, and TextFocusTracker's state machine (what each subscriber is sent and when).
#   Tests/checks/text-focus/run.sh   compile and run the check
here="$(cd "$(dirname "$0")" && pwd)"
source "$here/../common.sh"
swiftc -O Sources/StreamProtocol/*.swift "$here/main.swift" -o "$out/check"
"$out/check"
