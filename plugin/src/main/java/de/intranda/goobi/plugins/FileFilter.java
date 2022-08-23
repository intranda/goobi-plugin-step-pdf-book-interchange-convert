package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;

public class FileFilter {

    private static StorageProviderInterface SPI = StorageProvider.getInstance();

    private static boolean regexFileFilter(Path path, String regex) {

        try {
            return !Files.isDirectory(path) && !Files.isHidden(path) && path.getFileName().toString().matches(regex);

        } catch (IOException e) {
            // if we can't open it we will not add it to the List
            return false;
        }
    }

    public static List<Path> getPdfFiles(Path folder) {
        return SPI.listFiles(folder.toString(), path -> {
            return regexFileFilter(path, "(?i).*\\.pdf$");
        });
    }

    public static List<Path> getXmlFiles(Path folder) {
        return SPI.listFiles(folder.toString(), path -> {
            return regexFileFilter(path, "(?i).*\\.xml$");
        });
    }
}
