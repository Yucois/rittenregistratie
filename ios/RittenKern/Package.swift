// swift-tools-version:5.9
import PackageDescription

// De fiscale rekenregels, los van iOS, zodat ze met `swift test` te controleren
// zijn. Dit is een vertaling van kern/ uit de Android-app; de tests zijn
// dezelfde gevallen.
let package = Package(
    name: "RittenKern",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [
        .library(name: "RittenKern", targets: ["RittenKern"]),
    ],
    targets: [
        .target(name: "RittenKern"),
        .testTarget(name: "RittenKernTests", dependencies: ["RittenKern"]),
    ]
)
