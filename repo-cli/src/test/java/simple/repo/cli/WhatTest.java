package simple.repo.cli;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static simple.repo.cli.SimpleRepoApplication.instance;

public class WhatTest {
    @Test
    void test() {
        var rel = UriComponentsBuilder.fromUri(URI.create("file:./rel/a/b/c")).build();
        var rel1 = UriComponentsBuilder.fromUri(URI.create("file:/rel/a/b/c")).build();
        var rel2 = UriComponentsBuilder.fromUri(URI.create("file://rel/a/b/c")).build();
        var absolute = UriComponentsBuilder.fromUri(URI.create("file:///absolute/a/b/c")).build();
        var prefix = UriComponentsBuilder.fromUri(URI.create("s3://bucket/prefix")).build();
        System.out.println();
    }
}
