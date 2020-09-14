package com.wandisco.watcher;

public interface WatchHandler<ValueT> {
  void create(ValueT value);
  void modify(ValueT value);
  void delete(ValueT value);
}
