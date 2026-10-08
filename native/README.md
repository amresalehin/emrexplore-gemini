# ExifTool Android runtime

emrexplore uses ExifTool as the canonical metadata reader/writer. ExifTool is a Perl application, so Android packaging uses an ABI-specific native launcher installed through `jniLibs`. Kotlin invokes that launcher with an argv list and never through a shell.

Inspection uses ExifTool JSON with `-j -G1 -a -s -n -struct`. SAF URIs are copied to app-private temporary files before reads/writes and written back only after a successful metadata operation.

The upstream ExifTool repository currently reports version 13.59. See the official project repository for source and distribution details.
