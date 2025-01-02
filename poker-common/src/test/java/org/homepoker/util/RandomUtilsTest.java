package org.homepoker.util;

import org.homepoker.lib.util.RandomUtils;
import org.junit.jupiter.api.Test;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public class RandomUtilsTest {
  @Test
  void shuffleCollectionSpeed() {
    List<Integer> collection = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

    StopWatch stopWatch = new StopWatch();

    stopWatch.start();
    for (int i = 0; i < 10_000_000; i++) {
      RandomUtils.shuffleCollection(collection);
    }
    stopWatch.stop();
    System.out.println(" Time = " + stopWatch.getTotalTimeMillis());
  }

}
