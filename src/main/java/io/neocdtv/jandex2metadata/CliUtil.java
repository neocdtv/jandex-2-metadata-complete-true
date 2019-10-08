package io.neocdtv.jandex2metadata;

/**
 * @author xix
 */
public class CliUtil {

  static String findCommandArgumentByName(final String argNameToFind, final String[] args) {
    for (String argToCheck : args) {
      final String[] split = argToCheck.split("=");
      if (split[0].equals(argNameToFind)) {
        return split[1];
      }
    }
    return null;
  }
}