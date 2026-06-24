package simple.repo.model;

public record FileSpecWithContent(
        String path,
        Integer mode,
        FileIntegrityWithContent content
) {
    public FileSpecWithContent(PackageConfig.TarFileSpec tarFileSpec, FileIntegrityWithContent fileIntegrityWithContent) {
        this(tarFileSpec.getPath(), tarFileSpec.getMode(), fileIntegrityWithContent);
    }

    public FileSpecWithContent(FileIntegrityWithContent fileIntegrityWithContent, Integer mode) {
        this(fileIntegrityWithContent.getFileIntegrity().getPath(), mode, fileIntegrityWithContent);
    }
}
