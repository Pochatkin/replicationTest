package com.wandisco.watcher;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;


@SuppressWarnings("ResultOfMethodCallIgnored")
public class WatcherServiceImpl {
  private final WatchHandler<Path> defaultHandler;
  private final WatchService myWatchService;
  private final File masterDir;
  private final String[] targetDirs;

  public WatcherServiceImpl(String masterDir, String[] repDirs) throws IOException {
    this(masterDir, repDirs, null);
  }

  public WatcherServiceImpl(String masterDir, String[] repDirs, WatchHandler<Path> handler) throws IOException {
    this.targetDirs = repDirs;
    this.masterDir = new File(masterDir);
    this.masterDir.mkdirs();
    myWatchService = FileSystems.getDefault().newWatchService();
    registerAll(this.masterDir.toPath());
    defaultHandler = handler != null ? handler : createDefaultHandler();
  }

  private WatchHandler<Path> createDefaultHandler() {
    return new WatchHandler<>() {
      @Override
      public void create(Path path) {
        Path relativePath = relativePathFromMaster(path);
        doForAllTargets(targetDir -> {
          File workingDir = new File(targetDir, relativePath.getParent().toString());
          copy(workingDir, path.toFile());
        });
      }

      @Override
      public void modify(Path path) {
        Path relativePath = relativePathFromMaster(path);
        doForAllTargets(targetDir -> {
          File fileToModify = new File(targetDir, relativePath.toString());
          fileToModify.setWritable(true);
          File fileFromModify = new File(masterDir, relativePath.toString());
          writeFile(fileToModify, fileFromModify);
        });
      }

      @Override
      public void delete(Path path) {
        Path relativePath = relativePathFromMaster(path);
        doForAllTargets(targetDir -> {
          File fileToDelete = new File(targetDir, relativePath.toString());
          boolean delete = false;
          try {
            delete = Files.deleteIfExists(fileToDelete.toPath());
          } catch (IOException e) {
            System.out.println("Problem with deleting " + fileToDelete.getAbsolutePath() + "; " + e.getMessage());
          }
          if (!delete) {
            System.out.println("File " + fileToDelete.getAbsolutePath() + " not deleted!");
          }
        });
      }
    };
  }

  public void writeFile(File to, File from) {
    try {
      checkAndWrite(to, Files.readString(from.toPath(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      System.err.println("writefile error " + to.getAbsolutePath() + ", " + e.getMessage());
    }
  }

  private void checkAndWrite(File file, String content) throws IOException {
    try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
      writer.write(content);
    }
  }


  private void doForAllTargets(Consumer<String> action) {
    for (String targetDir : targetDirs) {
      action.accept(targetDir);
    }
  }

  private void registerAll(Path start) {
    try {
      Files.walkFileTree(start, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          checkTargetsConsistency(dir);
          dir.register(myWatchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE);
          return FileVisitResult.CONTINUE;
        }

      });
    } catch (IOException e) {
      System.err.println("Problem with tree walker: " + e.getMessage());
    }
  }

  private Path relativePathFromMaster(Path path) {
    return masterDir.toPath().relativize(path);
  }

  private void checkTargetsConsistency(Path path) {
    Path relativePath = relativePathFromMaster(path);
    doForAllTargets(target -> doCheck(target, relativePath));
  }


  private void doCheck(String target, Path relativePath) {
    String relativePathStr = relativePath.toString();
    File targetFolder = new File(target, relativePathStr);
    targetFolder.setWritable(true);
    File sourceFolder = new File(masterDir, relativePathStr);
    File[] files = sourceFolder.listFiles();
    if (files != null) {
      for (File child : files) {
        copy(targetFolder, child);
      }
    }
  }

  private void copy(File currentFolder, File target) {
    if (target.isDirectory()) {
      new File(currentFolder, target.getName()).mkdirs();
      File[] files = target.listFiles();
      if (files != null) {
        for (File file : files) {
          copy(new File(currentFolder, target.getName()), file);
        }
      }
    } else {
      try {
        File targetFile = new File(currentFolder, target.getName());
        targetFile.setWritable(true);
        if (!targetFile.exists()) {
          targetFile.createNewFile();
        }
        writeFile(targetFile, target);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void update() {
    if (myWatchService != null) {
      WatchKey key;
      while ((key = myWatchService.poll()) != null) {
        List<WatchEvent<?>> watchEvents = key.pollEvents();
        if (watchEvents.size() == 0) continue;

        WatchEvent<?> event = watchEvents.get(watchEvents.size() - 1);
        WatchEvent.Kind<?> kind = event.kind();
        Path context = (Path) event.context();
        Path resolve = ((Path) key.watchable()).resolve(context);
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
          defaultHandler.delete(resolve);
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
          defaultHandler.modify(resolve);
        } else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
          defaultHandler.create(resolve);
          if (resolve.toFile().isDirectory()) {
            try {
              resolve.register(myWatchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE);
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }
        key.reset();
      }
    }
  }
}
