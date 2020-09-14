package com.wandisco.util;

public interface Disposable {

  void dispose();


  static Disposable composite(Disposable reg1, Disposable reg2) {
    return () -> {
      reg1.dispose();
      reg2.dispose();
    };
  }

  static Disposable safeDispose(Disposable reg) {
    if (reg != null) {
      reg.dispose();
    }
    return null;
  }
}
