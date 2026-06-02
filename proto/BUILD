load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

proto_library(
    name = "cache_proto",
    srcs = ["cache.proto"],
)

java_proto_library(
    name = "cache_java_proto",
    visibility = ["//visibility:public"],
    deps = [":cache_proto"],
)
