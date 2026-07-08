package simple.repo.model;

public record FileSpecWithContent(
        String path,
        Integer mode,
        FileIntegrityWithContent content
) {
    public FileSpecWithContent(PackageConfig.PkgFileSpec pkgFileSpec, FileIntegrityWithContent fileIntegrityWithContent) {
        this(pkgFileSpec.getPath(), pkgFileSpec.getMode(), fileIntegrityWithContent);
    }

    public FileSpecWithContent(FileIntegrityWithContent fileIntegrityWithContent, Integer mode) {
        this(fileIntegrityWithContent.getFileIntegrity().getPath(), mode, fileIntegrityWithContent);
    }
}
