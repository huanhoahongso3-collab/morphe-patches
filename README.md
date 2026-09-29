# Spicetify Android patches

Spotify Android customizations for use with [Morphe](https://morphe.software/).
This repository publishes patch source and bundles, not Spotify APKs.

<!-- prettier-ignore -->
> [!NOTE]
> This is an experimental feature currently under active development.
> The initial target is Spotify 9.1.80.2221, ARM64. Runtime compatibility is
> still being verified.

## Patches

The first milestone contains two patches. Colors are optional because they
change only selected Android resources, not every Spotify screen.

| Patch | Default | Behavior |
| --- | --- | --- |
| Block ads | Enabled | Blocks banner, pop-up, home feed, and browse ads around the app without modifying audio playback. |
| Hide app detection | Enabled | Hides app modifications and prevents Spotify from detecting the patched client by disabling integrity verification reporting and spoofing official package signatures. |
| Clean sharing links | Enabled | Removes `si`, `pi`, and known `utm_*` parameters from `open.spotify.com` links. Preserves timestamps, context, other parameters, and fragments. |
| Theme colors | Disabled | Sets selected background, accent, and pressed-accent colors. The default background is AMOLED black. Hardcoded colors and animations can retain Spotify's colors. |

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/huanhoahongso3-collab/morphe-patches/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 Spotify&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block ads](#block-ads) | Blocks banner, pop-up, home feed, and browse ads around the app without modifying audio playback. |  |
| [Clean sharing links](#clean-sharing-links) | Removes sharing identifiers and marketing parameters from open.spotify.com links. Keeps playback timestamps, context, and other parameters. |  |
| [Hide app detection](#hide-app-detection) | Hides app modifications and prevents Spotify from detecting the patched client by disabling integrity verification reporting and spoofing official package signatures. |  |
| [Theme colors](#theme-colors) | Changes selected background and accent color resources; defaults to AMOLED black. Some screens, hardcoded colors, and animations retain Spotify's colors. | • Primary background color<br>• Accent color<br>• Pressed accent color |

</details>

<!-- PATCHES_END -->

## Add source to Morphe Manager

To add this patch source to Morphe Manager:

1. Open **Sources**, select **Add**, and choose **Remote**.
2. Paste the following source URL, then select **Add**:

   ```text
   https://raw.githubusercontent.com/huanhoahongso3-collab/morphe-patches/main/patches-bundle.json
   ```

   Or add via GitHub repository slug: `huanhoahongso3-collab/morphe-patches`.

3. Return to the app list and select **Spotify**. All patches support any version of Spotify with no version limitation.
4. Select your Spotify APK or split-APK archive and choose the desired patches:
   - **Block ads** (blocks in-app display ads, pop-up ads, home & browse feed ads)
   - **Hide app detection** (disables Play Integrity reporting and spoofs official signatures)
   - **Clean sharing links** (removes tracking parameters from shared links)
   - **Theme colors** (optional AMOLED/accent colors)
5. Optional: To customize colors, open **Settings > Advanced** and enable
   **Expert mode** before selecting Spotify. In Expert mode, you can customize
   background and accent colors under **Theme colors**.
6. Tap **Patch** and wait for **Patching complete**.
7. Tap **Install** and confirm installation in Android's dialog. Open Spotify and sign in.

This feed stays on experimental releases. Keep your own stock APK or split-APK
archive for patching; the repository does not distribute Spotify.

## Try a local build

Build the bundle using the [development instructions](CONTRIBUTING.md), then
load `patches/build/libs/patches-*.mpp` in
[Morphe Desktop](https://github.com/MorpheApp/morphe-desktop). Select your own
stock Spotify APK or split-APK archive matching the declared target.

Keep the patcher's signing key if you use Desktop for later updates. A
Desktop-signed APK and a Manager-signed APK can use different keys.

## Development

Read [CONTRIBUTING.md](CONTRIBUTING.md) for setup, tests, and release steps.

This project is independent of Spotify and the Morphe project. Its repository
slug is `morphe-patches`; its display name is Spicetify Android patches.

## License

The patch code is licensed under [GPL-3.0](LICENSE), with the upstream
[NOTICE](NOTICE) retained. See [third-party sources](THIRD_PARTY_NOTICES.md)
for the template and historical implementations used during development.
