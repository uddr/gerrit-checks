package(
    default_testonly = True,
    default_visibility = ["//plugins/checks:visibility"],
)

java_library(
    name = "testsuite",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//plugins/checks:checks__plugin",
    ],
)
