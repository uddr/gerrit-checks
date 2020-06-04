load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

package(default_visibility = ["//plugins/checks:visibility"])

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "checks",
    deps = [
        "//java/com/google/gerrit/server/util/time",
        "//javatests/com/google/gerrit/acceptance/rest/util",
        "//plugins/checks:checks__plugin",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance",
        "//plugins/checks/java/com/google/gerrit/plugins/checks/acceptance/testsuite",
    ],
)
