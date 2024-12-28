package dev.lukebemish.unmergetool.cli;

import dev.lukebemish.unmergetool.common.Distribution;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@CommandLine.Command(name = "unmergetool", mixinStandardHelpOptions = true, description = "Strip out elements from improper distributions from a jar")
public class Main implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = "--input", description = "Input jar", required = true)
    Path input;

    @CommandLine.Option(names = "--output", description = "Output jar", required = true)
    Path output;

    @CommandLine.Option(names = "--target-classes", description = "Output list of classes targeted", required = false)
    Path targetClasses;

    @CommandLine.Option(names = "--distribution", description = "The distribution to keep elements from", required = true)
    Distribution distribution;

    @CommandLine.Option(names = "--batch-size", description = "How many class files to process at once")
    int batchSize = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        var exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        input = input.toAbsolutePath();
        output = output.toAbsolutePath();

        try {
            Files.createDirectories(output.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (targetClasses != null) {
            targetClasses = targetClasses.toAbsolutePath();
        }
        var targeted = new HashSet<String>();

        try (var is = Files.newInputStream(input);
             var os = Files.newOutputStream(output);
             var zis = new ZipInputStream(is);
             var zos = new ZipOutputStream(os)) {
            Entry[] entries = new Entry[batchSize];
            byte[][] labelled = new byte[batchSize][];
            Future<?>[] futures = new Future[batchSize];
            Set<String> excludedClasses = new HashSet<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                int i = 0;
                while (i < batchSize && entry != null) {
                    var bytes = new ByteArrayOutputStream();
                    zis.transferTo(bytes);
                    var finalBytes = bytes.toByteArray();
                    if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                        Manifest manifest = new Manifest(new ByteArrayInputStream(finalBytes));
                        for (var attr : distribution.manifestExcludedClasses) {
                            var found = manifest.getMainAttributes().getValue(attr);
                            if (found != null) {
                                excludedClasses.addAll(Arrays.asList(found.split(";")));
                            }
                        }
                    }
                    entries[i] = new Entry(entry, finalBytes);
                    i++;
                    if (i < batchSize) {
                        entry = zis.getNextEntry();
                    }
                }
                for (; i < batchSize; i++) {
                    entries[i] = null;
                    labelled[i] = new byte[0];
                }

                processEntries(entries, labelled, futures, excludedClasses, targeted);

                for (int j = 0; j < batchSize; j++) {
                    var entryIn = entries[j];
                    if (entryIn == null) {
                        continue;
                    }
                    ZipEntry zipEntry = entryIn.entry();
                    var newEntry = new ZipEntry(zipEntry.getName());
                    if (zipEntry.getExtra() != null) {
                        newEntry.setExtra(zipEntry.getExtra());
                    }
                    if (zipEntry.getLastAccessTime() != null) {
                        newEntry.setLastAccessTime(zipEntry.getLastAccessTime());
                    }
                    if (zipEntry.getLastModifiedTime() != null) {
                        newEntry.setLastModifiedTime(zipEntry.getLastModifiedTime());
                    }
                    if (zipEntry.getCreationTime() != null) {
                        newEntry.setCreationTime(zipEntry.getCreationTime());
                    }
                    if (zipEntry.getComment() != null) {
                        newEntry.setComment(zipEntry.getComment());
                    }
                    byte[] bytes = labelled[j];
                    zos.putNextEntry(newEntry);
                    zos.write(bytes);
                    zos.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (targetClasses != null) {
            try {
                List<String> targetedLines = new ArrayList<>(targeted);
                targetedLines.sort(Comparator.naturalOrder());
                Files.write(targetClasses, targetedLines);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void processEntries(Entry[] entries, byte[][] labelled, Future<?>[] futures, Set<String> excludedClasses, Set<String> targetedClasses) {
        for (int i = 0; i < batchSize; i++) {
            var entry = entries[i];
            if (entry == null) {
                continue;
            }
            var number = i;
            futures[i] = executorService.submit(() -> {
                if (excludedClasses.contains(entry.entry().getName())) {
                    entries[number] = null;
                    return;
                }
                if (entry.entry().getName().endsWith(".class")) {
                    var reader = new ClassReader(entry.contents());
                    var collector = new CollectingVisitor(null, distribution);
                    boolean anythingStripped = collector.shouldRemove() || !collector.removeFields().isEmpty() || collector.removeMethods().isEmpty();
                    if (anythingStripped) {
                        synchronized (targetedClasses) {
                            targetedClasses.add(entry.entry().getName());
                        }
                    }
                    reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (collector.shouldRemove()) {
                        entries[number] = null;
                        return;
                    }
                    var writer = new ClassWriter(0);
                    reader.accept(new ProcessingVisitor(writer, collector), 0);
                    labelled[number] = writer.toByteArray();
                } else {
                    labelled[number] = entry.contents();
                }
            });
        }
        for (int i = 0; i < batchSize; i++) {
            try {
                futures[i].get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private record Entry(ZipEntry entry, byte[] contents) {}

    private final ExecutorService executorService = Executors.newFixedThreadPool(batchSize);
}
