package org.homepoker.lib.util;

import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Utility class for random operations to abstract away the specifics of the random number generator. All methods are
 * thread-safe and can be used from virtual threads.
 */
public class RandomUtils {

  static RandomGenerator.SplittableGenerator sourceGenerator = RandomGeneratorFactory
      .<RandomGenerator.SplittableGenerator>of("L128X256MixRandom")
      .create();


  public static synchronized void shuffleCollection(List<?> collection) {
    Collections.shuffle(collection, sourceGenerator);
  }

}
