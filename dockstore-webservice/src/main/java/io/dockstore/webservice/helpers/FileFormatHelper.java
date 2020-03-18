package io.dockstore.webservice.helpers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.xml.bind.DatatypeConverter;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.languages.CWLHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class helps resolve file formats in versions
 * @author gluu
 * @since 1.5.0
 */
public final class FileFormatHelper {
    private static final Logger LOG = LoggerFactory.getLogger(FileFormatHelper.class);
    private FileFormatHelper() { }

    /**
     * Updates the given tool/workflow to show which file formats are associated with its sourcefiles
     * @param versions  A tool/workflow's versions (tags/workflowVersions)
     * @param fileFormatDAO  The FileFormatDAO to check the FileFormat table
     */
    public static void updateFileFormats(Set<? extends Version> versions, final FileFormatDAO fileFormatDAO) {
        CWLHandler cwlHandler = new CWLHandler();
        versions.forEach(tag -> {
            SortedSet<FileFormat> inputFileFormats = new TreeSet<>();
            SortedSet<FileFormat> outputFileFormats = new TreeSet<>();
            SortedSet<SourceFile> sourceFiles = tag.getSourceFiles();
            List<SourceFile> cwlFiles = sourceFiles.stream()
                    .filter(sourceFile -> sourceFile.getType() == DescriptorLanguage.FileType.DOCKSTORE_CWL).collect(Collectors.toList());
            cwlFiles.stream().filter(cwlFile -> cwlFile.getContent() != null).forEach(cwlFile -> {
                inputFileFormats.addAll(cwlHandler.getFileFormats(cwlFile.getContent(), "inputs"));
                outputFileFormats.addAll(cwlHandler.getFileFormats(cwlFile.getContent(), "outputs"));
            });
            SortedSet<FileFormat> realInputFileFormats = getFileFormatsFromDatabase(fileFormatDAO, inputFileFormats);
            SortedSet<FileFormat> realOutputFileFormats = getFileFormatsFromDatabase(fileFormatDAO, outputFileFormats);
            tag.setInputFileFormats(realInputFileFormats);
            tag.setOutputFileFormats(realOutputFileFormats);
        });
    }

    /**
     * The original set of FileFormats contains FileFormats already present in the DB.  This uses the one from the DB to avoid duplicates.
     * @param fileFormatDAO The FileFormatDAO used to access the DB
     * @param fileFormats   The original set of FileFormats that may contain duplicates from the DB.
     * @return the merged set of fileformats
     */
    private static SortedSet<FileFormat> getFileFormatsFromDatabase(FileFormatDAO fileFormatDAO, SortedSet<FileFormat> fileFormats) {
        SortedSet<FileFormat> fileFormatsFromDB = new TreeSet<>();
        fileFormats.forEach(fileFormat -> {
            FileFormat fileFormatFromDB = fileFormatDAO.findFileFormatByValue(fileFormat.getValue());
            if (fileFormatFromDB != null) {
                fileFormatsFromDB.add((fileFormatFromDB));
            } else {
                fileFormatFromDB = new FileFormat();
                fileFormatFromDB.setValue(fileFormat.getValue());
                String id = fileFormatDAO.create(fileFormatFromDB);
                fileFormatsFromDB.add(fileFormatDAO.findFileFormatByValue(id));
            }
        });
        return fileFormatsFromDB;
    }

    public static Optional<String> calcSHA1(String content) {
        if (content != null) {
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
                final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                messageDigest.update(bytes, 0, bytes.length);
                String sha1 = DatatypeConverter.printHexBinary(messageDigest.digest()).toLowerCase();
                return Optional.of(sha1);
            } catch (UnsupportedOperationException | NoSuchAlgorithmException ex) {
                LOG.error("Unable to calculate SHA-1", ex);
            }
        } else {
            LOG.error("File descriptor content is null");
        }
        return Optional.empty();
    }
}
