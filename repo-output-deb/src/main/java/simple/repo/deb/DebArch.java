package simple.repo.deb;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import simple.repo.model.Arch;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code any} = "the code has been written portably", {@code all} = "do not need to be compiled"
 *
 * @see <a href=https://wiki.debian.org/Multiarch>Multiarch</a>
 * @see <a href=https://wiki.debian.org/Packaging/Intro>Packaging/Intro</a>
 */
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
    ;

    private static final Map<String, DebArch> MAP = Arrays.stream(values())
            .collect(Collectors.toMap(DebArch::getJdkArchName, Function.identity()));

    private final String jdkArchName;

    public static DebArch fromArch(Arch arch) {
        return MAP.get(arch.name());
    }
}
