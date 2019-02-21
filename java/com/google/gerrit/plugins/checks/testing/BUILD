package(
    default_testonly = True,
    default_visibility = ["//plugins/checks:visibility"],
)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/testing",
        "//java/com/google/gerrit/truth",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//plugins:plugin-api",
        "//plugins/checks:checks__plugin",
    ],
)
