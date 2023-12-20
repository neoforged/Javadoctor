package net.neoforged.javadoctor.injector.jbpsi;

import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Map;

public class CoreJrtFileSystem extends CoreLocalFileSystem {
    private final CoreLocalVirtualFile rootFile;

    public CoreJrtFileSystem() {
        final String jdkHomePath = System.getProperty("java.home");
        final var jdkHome = new File(jdkHomePath);
        final var rootUri = URI.create(StandardFileSystems.JRT_PROTOCOL + ":/");
        final FileSystem fileSystem;
        try {
            fileSystem = FileSystems.newFileSystem(rootUri, Map.of("java.home", jdkHome.getAbsolutePath()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.rootFile = new CoreLocalVirtualFile(this, fileSystem.getPath(""));
    }

    @Override
    public @NonNls @NotNull String getProtocol() {
        return StandardFileSystems.JRT_PROTOCOL;
    }

    @Override
    public @Nullable VirtualFile findFileByPath(@NotNull @NonNls String path) {
        return rootFile.findFileByRelativePath(path);
    }

    @Override
    public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
        return findFileByPath(path);
    }
}
