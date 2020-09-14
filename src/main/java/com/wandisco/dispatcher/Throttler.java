package com.wandisco.dispatcher;

import com.wandisco.util.Disposable;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Throttler {

  private final EventDispatcher dispatcher;

  public Throttler(EventDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  public <T, R> Function<T, R> wrapThrottle(Function<T, R> action, int delay) {
    Task<T, R> result = new Task<>(action);
    dispatcher.scheduleRepeating(result::update, delay);
    return result;
  }

  public static class Task<T, R> implements Function<T, R> {
    private final Function<T, R> function;
    private T lastValue;
    private R cachedValue;

    public Task(Function<T, R> function) {
      this.function = function;
    }

    public void update() {
      cachedValue = function.apply(lastValue);
    }

    @Override
    public R apply(T t) {
      lastValue = t;
      if (cachedValue == null) update();
      return cachedValue;
    }
  }

  public interface EventDispatcher {
    Disposable schedule(Runnable r, int delay);
    Disposable scheduleRepeating(Runnable r, int repeating);
  }

  public static class EventDispatcherImpl implements EventDispatcher {
    private final ScheduledExecutorService service;

    public EventDispatcherImpl(int poolSize) {
      service = Executors.newScheduledThreadPool(poolSize);
    }

    @Override
    public Disposable schedule(Runnable r, int delay) {
      Future<?> future = service.schedule(r, delay, TimeUnit.MILLISECONDS);
      return () -> future.cancel(false);
    }

    @Override
    public Disposable scheduleRepeating(Runnable r, int repeating) {
      Future<?> future = service.scheduleAtFixedRate(r, 0, repeating, TimeUnit.MILLISECONDS);
      return () -> future.cancel(false);
    }
  }
}
