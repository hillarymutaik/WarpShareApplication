package org.mokee.warpshare.airdrop;

import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IRGRP;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IROTH;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IRUSR;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_ISREG;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.C_IWUSR;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.FORMAT_OLD_ASCII;

import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.mokee.warpshare.GossipyInputStream;
import org.mokee.warpshare.base.Entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;

class AirDropArchiveUtil {

    static void pack(List<Entity> entities, OutputStream output,
                     GossipyInputStream.Listener streamReadListener) throws IOException {
        try (final GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(output);
             final CpioArchiveOutputStream cpio = new CpioArchiveOutputStream(gzip, FORMAT_OLD_ASCII)) {
            for (Entity entity : entities) {
                final CpioArchiveEntry entry = new CpioArchiveEntry(FORMAT_OLD_ASCII, entity.path());
                entry.setMode(C_ISREG | C_IRUSR | C_IWUSR | C_IRGRP | C_IROTH);
                entry.setTime(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));

                final InputStream stream = new GossipyInputStream(entity.stream(), streamReadListener);
                final BufferedSource source = Okio.buffer(Okio.source(stream));
                final long size = entity.size();
                if (size == -1) {
                    final ByteString content = source.readByteString();
                    entry.setSize(content.size());
                    cpio.putArchiveEntry(entry);
                    content.write(cpio);
                } else {
                    entry.setSize(size);
                    cpio.putArchiveEntry(entry);
                    source.readAll(Okio.sink(cpio));
                }

                cpio.closeArchiveEntry();
            }
        }
    }

    static void unpack(InputStream input, Set<String> paths, FileFactory factory) throws IOException {
        try (final GzipCompressorInputStream gzip = new GzipCompressorInputStream(input);
             final CpioArchiveInputStream cpio = new CpioArchiveInputStream(gzip)) {
            CpioArchiveEntry entry;
            while ((entry = cpio.getNextCPIOEntry()) != null) {
                if (entry.isRegularFile() && paths.contains(entry.getName())) {
                    factory.onFile(entry.getName(), entry.getSize(), cpio);
                }
            }
        }
    }

    public interface FileFactory {

        void onFile(String name, long size, InputStream input);

    }

}
