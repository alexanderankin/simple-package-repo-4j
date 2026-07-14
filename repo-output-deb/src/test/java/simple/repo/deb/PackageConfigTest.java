package simple.repo.deb;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import simple.repo.model.PackageConfig;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class PackageConfigTest {
    JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    YAMLMapper yamlMapper = YAMLMapper.builder().findAndAddModules().build();

    @SneakyThrows
    @Test
    void deserializesFileSpecSubtypes() {
        var result = objectMapper.readValue("""
                {
                    "controlFiles": [{"type": "text", "content": "echo hi", "path": "postinst", "mode": "0x755" }],
                    "dataFiles": [{"type": "binary", "content": "aGVsbG8=", "comment": "hello", "path": "/etc/hello"}]
                }
                """, PackageConfig.FileSpec.class);

        assertThat(result.getControlFiles().getFirst(), is(instanceOf(PackageConfig.PkgFileSpec.TextPkgFileSpec.class)));
        assertThat(result.getDataFiles().getFirst(), is(instanceOf(PackageConfig.PkgFileSpec.BinaryPkgFileSpec.class)));
        assertThat(((PackageConfig.PkgFileSpec.BinaryPkgFileSpec) result.getDataFiles().getFirst()).getContent(),
                is("hello".getBytes()));
        assertThat(objectMapper.writer().withDefaultPrettyPrinter().writeValueAsString(result), containsString("aGVsbG8="));
    }

    @SneakyThrows
    @Test
    void deserializesYamlPackageConfig() {
        var result = yamlMapper.readValue("""
                meta:
                  name: test
                  version: 0.0.1
                  arch: amd64
                files:
                  controlFiles: []
                  dataFiles:
                    - type: text
                      content: blah
                      path: /etc/example
                control:
                  depends: ""
                  recommends: ""
                  section: main
                  priority: optional
                  homepage: ""
                  maintainer: maintainer
                  description: description
                """, PackageConfig.class);

        assertThat(result.getMeta().getName(), is("test"));
        assertThat(result.getFiles().getDataFiles().getFirst(),
                is(instanceOf(PackageConfig.PkgFileSpec.TextPkgFileSpec.class)));
        assertThat(result.getControl().getPriority(), is("optional"));
    }
}
