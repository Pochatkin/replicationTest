package com.wandisco;

import com.wandisco.watcher.WatcherServiceImpl;

import java.io.IOException;
import java.util.Arrays;

public class EntryPoint {
  @SuppressWarnings("BusyWait")
  public static void main(String[] args) throws IOException {
    if (args.length < 2) throw new IllegalArgumentException("Please specify the master and targets folders");
    String masterDir = args[0];

    WatcherServiceImpl watcherService = new WatcherServiceImpl(
        masterDir,
        Arrays.copyOfRange(args, 1, args.length - 1)
    );
    while (true) {
      watcherService.update();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
