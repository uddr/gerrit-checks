load("@rules_proto//proto:defs.bzl", "proto_library")
load("@rules_java//java:defs.bzl", "java_proto_library")

package(default_visibility = ["//plugins/checks:visibility"])

proto_library(
    name = "cache_proto",
    srcs = ["cache.proto"],
)

java_proto_library(
    name = "cache_java_proto",
    visibility = ["//visibility:public"],
    deps = [":cache_proto"],
)
