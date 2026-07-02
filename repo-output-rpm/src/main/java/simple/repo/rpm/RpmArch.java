package simple.repo.rpm;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import simple.repo.model.Arch;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum RpmArch {
    x86_64(Arch.amd64),
    aarch64(Arch.arm64),
    // /**
    //  * this is not supported
    //  *
    //  * @see <a href=https://fedoraproject.org/wiki/Architectures/ARM>source</a>
    //  * @see <a href=https://docs.fedoraproject.org/en-US/quick-docs/raspberry-pi/>source</a>
    //  */
    // armhfp(Arch.arm),
    // /**
    //  * this is not officially supported
    //  *
    //  * @see <a href=https://fedoraproject.org/wiki/Architectures/RISC-V/Installing>source</a>
    //  */
    // riscv64(Arch.riscv64),
    ;

    private static final Map<Arch, RpmArch> MAP = Arrays.stream(values())
            .collect(Collectors.toMap(RpmArch::getArch, Function.identity()));

    private final Arch arch;

    public static RpmArch fromArch(Arch arch) {
        return MAP.get(arch);
    }
}
