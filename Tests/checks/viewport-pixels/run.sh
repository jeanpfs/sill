#!/bin/bash
# Viewport's pixel size (pixelWidth, pixelHeight) and the capture scale the host derives from it:
# the wire names and what an older client's or host's decoder does with them, and the rule that a
# source is captured at the panel's own pixels, never above the configured scale and never below 1x.
#   Tests/checks/viewport-pixels/run.sh    compile and run the check
here="$(cd "$(dirname "$0")" && pwd)"
source "$here/../common.sh"
swiftc -O Sources/StreamProtocol/Viewport.swift Sources/StreamProtocol/StreamMessage.swift "$here/main.swift" -o "$out/check"
"$out/check"
