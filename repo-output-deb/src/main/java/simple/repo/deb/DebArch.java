package simple.repo.deb;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum DebArch {
    amd64("amd64"),
    arm64("aarch64"),
    /**
     * <code>sudo apt install qemu-system-arm</code>
     */
    armhf("arm"),
    /**
     * <code>sudo apt install qemu-system-misc</code>
     */
    riscv64("riscv64"),
    /**
     * package intended for all architectures
     */
    all("all"),
    ;

    private static final Map<String, DebArch> MAP = Arrays.stream(values())
            .collect(Collectors.toMap(DebArch::getJdkArchName, Function.identity()));

    private final String jdkArchName;

    public static DebArch fromJdkArchName(String jdkArchName) {
        return MAP.get(jdkArchName);
    }
}
