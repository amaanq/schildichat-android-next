{
  description = "SchildiChat Next - Matrix client for Android";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };

          androidComposition = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [
              "36"
              "35"
              "34"
              "33"
              "31"
            ];
            buildToolsVersions = [
              "36.0.0"
              "35.0.0"
              "34.0.0"
            ];
            includeNDK = false;
          };

          androidSdk = androidComposition.androidsdk;
        in
        {
          default = pkgs.mkShell {
            buildInputs = [
              androidSdk
              pkgs.jdk21
              pkgs.gradle_9
              pkgs.kotlin
              pkgs.git
            ];

            ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
            JAVA_HOME = "${pkgs.jdk21}";
            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/36.0.0/aapt2";

            shellHook = ''
              echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
              echo ""
              echo "SchildiChat Next Development Shell"
              echo "  JDK:         $(java -version 2>&1 | head -1)"
              echo "  Android SDK: $ANDROID_SDK_ROOT"
              echo ""
              echo "Build release:  ./gradlew assembleFdroidScDefaultRelease"
              echo "Build debug:    ./gradlew assembleFdroidScDefaultDebug"
              echo ""
            '';
          };
        }
      );
    };
}
