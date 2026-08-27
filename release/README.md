# release

`TubeWinder-win64.zip` is the standalone build for the club.

Extract the whole `TubeWinder` folder out of it, double-click `TubeWinder.bat`, done. Nothing needs
to be installed and no Java is required on the machine: a complete Java 21 runtime sits in the
`runtime` folder inside, the same way UGS ships its own `jdk` folder.

About 50 MB, roughly 150 MB extracted.

## Refreshing it

```
powershell -ExecutionPolicy Bypass -File .\tools\make-bundle.ps1
```

That rebuilds the jar, downloads the Windows runtime once into `target\`, lays out the folder and
rezips it. The launcher and the readme that go inside come from `tools\bundle-launcher.bat` and
`tools\bundle-readme.txt`, so edit those rather than anything inside the zip.

Rebuild this whenever you want the club to get changes; it does not update itself. On your own
machine use `TubeWinder.bat` at the top of the repo instead, which rebuilds every time you pull.

## Why a zip in the repo

So that "download the repo, extract, run" works for someone who has never installed a developer
tool. The honest downside is that a 50 MB binary lives in git history forever and every clone pays
for it. If that starts to hurt, the alternative is a GitHub Release: same zip, attached to a tag,
out of the repo itself.

The bundled runtime is Eclipse Temurin, redistributed under GPLv2 with the Classpath Exception.
Its licence files travel with it in `runtime\legal`.
