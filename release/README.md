# release

What the club downloads.

## The installer

`TubeWinder-<version>.exe` installs the app, puts a shortcut on the desktop and an entry in the
Start menu under Bike Builders, and registers an uninstaller in Add or Remove Programs. Nothing
needs to be installed first, not even Java: a complete Java 21 runtime is built in.

It installs per user, so Windows does not ask for an admin password. Installing a newer version
upgrades the old one in place.

Build a fresh one with:

```
powershell -ExecutionPolicy Bypass -File .\tools\package-app.ps1
```

It lands in `dist\`, which is gitignored. Attach it to a GitHub Release rather than committing it,
and the download button on the site finds it automatically.

## TubeWinder-win64.zip

The older portable bundle: extract the folder, double-click `TubeWinder.bat`. Superseded by the
installer, kept because the v0.1.0 release points at it. `tools\make-bundle.ps1` rebuilds it.

For a portable build with a real exe instead of a .bat, which is the shape UGS ships:

```
powershell -ExecutionPolicy Bypass -File .\tools\package-app.ps1 -Type portable
```

## Why not commit the binaries

A 50 MB file in git is paid for by every clone, forever. Releases are the right home: same download
experience, no repo weight. The site's download button points at the releases page, so a new release
needs no site change.

The bundled runtime is Eclipse Temurin, redistributed under GPLv2 with the Classpath Exception.
Its licence files travel with it.
