package com.ecovate.rtc.turn;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggingConfig {
  public static void configureLogging() {
    System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_DATE_TIME_KEY, "true");
    System.setProperty(org.slf4j.impl.SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd hh:mm:ss.SSS");
    try {
      final ConsoleHandler consoleHandler = new ConsoleHandler();
      consoleHandler.setLevel(Level.INFO);
      consoleHandler.setFormatter(new SimpleFormatter());
      final Logger app = Logger.getLogger("HTTPServer");
      app.setLevel(Level.OFF);
      app.addHandler(consoleHandler);
    } catch (Exception e) {
      // The runtime won't show stack traces if the exception is thrown
      e.printStackTrace();
    }
  }
}
