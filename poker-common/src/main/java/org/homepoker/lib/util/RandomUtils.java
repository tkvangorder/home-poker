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


  /**
   * Shuffles the specified collection using the default random number generator.
   *
   * @param collection the collection to shuffle
   */
  public static synchronized void shuffleCollection(List<?> collection) {
    Collections.shuffle(collection, sourceGenerator);
  }

  /**
   * Returns a random integer between 0 (inclusive) and the specified bound (exclusive).
   *
   * @param bound the upper bound (exclusive). Must be positive.
   * @return the random integer
   */
  public static synchronized int randomInt(int bound) {
    return sourceGenerator.nextInt(bound);
  }
}
