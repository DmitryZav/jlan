package org.alfresco.jlan.test.integration;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import static org.testng.Assert.*;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.testng.Reporter;

import org.alfresco.jlan.util.MemorySize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jcifs.smb.SmbFile;

/**
 * Files Per Folder Performance Test Class
 *
 * @author Fritz Elfert
 */
public class PerfFilesPerFolderIT extends ParameterizedJcifsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerfFilesPerFolderIT.class);

    // Maximum/minimum number of files
    private static final int MIN_FILECOUNT = 50;
    private static final int MAX_FILECOUNT = 5000;

    // Maximum/minimum allowed file size and write size
    private static final long MIN_FILESIZE = 1;// byte
    private static final long MAX_FILESIZE = 10 * MemorySize.MEGABYTE;
    private static final int MIN_WRITESIZE = 128;
    private static final int MAX_WRITESIZE = (int) (64 * MemorySize.KILOBYTE);

    // File name components
    //
    // File name format is '<prefix>_<mainpart>_<index>_<random>.txt'
    //
    // Prefixes
    private static final String[] PREFIXES = {
        "aaa", "bbb", "ccc", "ddd", "eee", "fff", "ggg", "hhh", "iii", "jjj", "kkk", "lll", "mmm", "nnn", "ooo", "ppp", "qqq",
        "rrr", "sss", "ttt", "uuu", "vvv", "www", "xxx", "yyy", "zzz", "AAA", "BBB", "CCC", "DDD", "EEE", "FFF", "GGG", "HHH",
        "III", "JJJ", "KKK", "LLL", "MMM", "NNN", "OOO", "PPP", "QQQ", "RRR", "SSS", "TTT", "UUU", "VVV", "WWW", "XXX", "YYY", "ZZZ"
    };

    // Test file name main part and extension

    private static final String TESTFILENAME = "_FilesPerFolder_";
    private static final String TESTFILEEXT = ".txt";

    /**
     * Default constructor
     */
    public PerfFilesPerFolderIT() {
        super("PerfFilesPerFolderIT");
    }

    private void doTest(final int iteration, final long fileSize, final int writeSize, final int filesPerFolder) throws Exception {
        final String testFolder = getPerTestFolderName(iteration);
        final SmbFile folder = new SmbFile(getRoot(), testFolder);
        folder.mkdir();
        assertTrue(folder.exists(), "Folder exists after create");
        // Allocate the I/O buffer
        final byte[] ioBuf = new byte[writeSize];
        // Record the start time
        final long startTime = System.currentTimeMillis();
        long endTime = 0L;
        // Create the test files
        int fileCnt = 1;
        final Random randNum = new Random();
        final StringBuilder testFileStr = new StringBuilder(128);
        while (fileCnt <= filesPerFolder) {
            // Create a unique file name
            testFileStr.setLength(0);
            testFileStr.append(testFolder);
            testFileStr.append(PREFIXES[ fileCnt % PREFIXES.length]);
            testFileStr.append(TESTFILENAME);
            testFileStr.append(fileCnt);
            testFileStr.append("_");
            testFileStr.append(Long.toHexString(randNum.nextLong()));
            testFileStr.append(TESTFILEEXT);
            final String testFileName = testFileStr.toString();
            registerFileNameForDelete(testFileName);
            // Fill the write buffer with a test pattern
            final byte testPat = (byte)PREFIXES[fileCnt % PREFIXES.length].charAt(0);
            Arrays.fill(ioBuf, testPat);
            // Create a new file
            final SmbFile testFile = new SmbFile(getRoot(), testFileName);
            testFile.createNewFile();
            assertTrue(testFile.exists(), "File exists after create");
            // Write to the file until we hit the required file size
            try (final OutputStream os = testFile.getOutputStream()) {
                long written = 0L;
                while (written < fileSize) {
                    // Write to the file
                    os.write(ioBuf);
                    // Update the file size
                    written += ioBuf.length;
                }
                os.flush();
            }
            // Update the file counter
            fileCnt++;
        }
        // Save the end time
        endTime = System.currentTimeMillis();
        // If there were no errors then output the elapsed time
        // Output the elapsed time
        long elapsedMs = endTime - startTime;
        int ms = (int) (elapsedMs % 1000L);
        long elapsedSecs = elapsedMs/1000;
        int secs = (int) (elapsedSecs % 60L);
        int mins = (int) ((elapsedSecs/60L) % 60L);
        int hrs  = (int) (elapsedSecs/3600L);
        String msg = String.format("Created %d files (size %s) in %02d:%02d:%02d.%03d (%dms)", filesPerFolder,
                MemorySize.asScaledString(fileSize), hrs, mins, secs, ms, elapsedMs);
        LOGGER.info(msg);
        Reporter.log(msg + "<br/>\n");
    }

    @Parameters({"iterations", "filesize", "writesize", "filecount"})
    @Test(groups = "perftest", singleThreaded = true)
    public void test(
        @Optional("1") final int iterations,
        @Optional("4K") final String fs,
        @Optional("4K") final String ws,
        @Optional("2000") final int filesPerFolder) throws Exception
    {
        long fileSize = 0;
        int writeSize = 0;
        try {
            fileSize = MemorySize.getByteValue(fs);
            if (fileSize < MIN_FILESIZE || fileSize > MAX_FILESIZE) {
                fail("Invalid file size (" + MIN_FILESIZE + " - " + MAX_FILESIZE + ")");
            }
        } catch (NumberFormatException ex) {
            fail("Invalid file size " + fs);
        }
        try {
            writeSize = MemorySize.getByteValueInt(ws);
            if (writeSize < MIN_WRITESIZE || writeSize > MAX_WRITESIZE) {
                fail("Invalid write buffer size (" + MIN_WRITESIZE + " - " + MAX_WRITESIZE + ")");
            }
        } catch (NumberFormatException ex) {
            fail("Invalid write size " + ws);
        }
        if (filesPerFolder < MIN_FILECOUNT || filesPerFolder > MAX_FILECOUNT) {
            fail("Invalid filecount (" + MIN_FILECOUNT + " - " + MAX_FILECOUNT + ")");
        }
        for (int i = 0; i < iterations; i++) {
            doTest(i, fileSize, writeSize, filesPerFolder);
        }
    }
}