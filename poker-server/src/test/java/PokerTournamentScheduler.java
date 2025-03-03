import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class PokerTournamentScheduler {
  // Constants
  private static final int HANDS_PER_HOUR = 30;
  private static final double PLAYERS_ELIMINATED_PER_LEVEL = 0.15;

  // Available chip denominations
  private static final NavigableSet<Integer> CHIP_DENOMINATIONS = new TreeSet<>(List.of(
      50, 100, 500, 1000, 5000, 10000
  ));

  public static class TournamentConfig {
    int numPlayers;
    int startingStack;
    int maxRebuys;
    boolean allowAddon;
    int rebuyStack;
    int addonStack;
    int rebuyEndLevel;
    double desiredDurationHours;

    public TournamentConfig(int numPlayers, int startingStack, int maxRebuys,
                            boolean allowAddon, int rebuyStack, int addonStack,
                            int rebuyEndLevel, double desiredDurationHours) {
      this.numPlayers = numPlayers;
      this.startingStack = startingStack;
      this.maxRebuys = maxRebuys;
      this.allowAddon = allowAddon;
      this.rebuyStack = rebuyStack;
      this.addonStack = addonStack;
      this.rebuyEndLevel = rebuyEndLevel;
      this.desiredDurationHours = desiredDurationHours;
    }
  }

  public static class BlindLevel {
    int level;
    int smallBlind;
    int bigBlind;
    int ante;
    int durationMinutes;
    String runningTime;
    boolean rebuysAllowed;
    boolean addonAvailable;
    String specialNotes;
    List<Integer> recommendedChips;

    public BlindLevel(int level, int smallBlind, int bigBlind, int ante,
                      int durationMinutes, String runningTime,
                      boolean rebuysAllowed, boolean addonAvailable) {
      this.level = level;
      this.smallBlind = smallBlind;
      this.bigBlind = bigBlind;
      this.ante = ante;
      this.durationMinutes = durationMinutes;
      this.runningTime = runningTime;
      this.rebuysAllowed = rebuysAllowed;
      this.addonAvailable = addonAvailable;
      this.specialNotes = "";
      this.recommendedChips = new ArrayList<>();
    }
  }

  private static int roundToNearestChipDenomination(int amount) {
    if (CHIP_DENOMINATIONS.contains(amount)) {
      return amount;
    }

    Integer ceiling = CHIP_DENOMINATIONS.ceiling(amount);
    Integer floor = CHIP_DENOMINATIONS.floor(amount);

    if (ceiling == null) return floor;
    if (floor == null) return ceiling;

    return (amount - floor <= ceiling - amount) ? floor : ceiling;
  }

  private static List<Integer> getRecommendedChips(int smallBlind, int bigBlind, int ante) {
    NavigableSet<Integer> needed = new TreeSet<>();

    // Add minimum chips needed for blinds and antes
    if (ante > 0) needed.add(ante);
    needed.add(smallBlind);
    needed.add(bigBlind);

    // Add chips needed for making change
    for (int denomination : CHIP_DENOMINATIONS) {
      if (denomination < smallBlind * 20) { // Keep some smaller chips for change
        needed.add(denomination);
      }
      if (denomination >= bigBlind * 50) { // No need for larger denominations yet
        break;
      }
    }

    return new ArrayList<>(needed);
  }

  public static class TournamentStats {
    int estimatedMaxChips;
    int estimatedMinPrizePool;
    int estimatedMaxPrizePool;

    public TournamentStats(int estimatedMaxChips, int estimatedMinPrizePool,
                           int estimatedMaxPrizePool) {
      this.estimatedMaxChips = estimatedMaxChips;
      this.estimatedMinPrizePool = estimatedMinPrizePool;
      this.estimatedMaxPrizePool = estimatedMaxPrizePool;
    }
  }

  public static class ScheduleResult {
    List<BlindLevel> schedule;
    TournamentStats stats;

    public ScheduleResult(List<BlindLevel> schedule, TournamentStats stats) {
      this.schedule = schedule;
      this.stats = stats;
    }
  }

  public static ScheduleResult calculateBlindSchedule(TournamentConfig config) {
    List<BlindLevel> blindSchedule = new ArrayList<>();

    // Calculate estimated maximum chips in play
    int maxChipsPerPlayer = config.startingStack +
        (config.maxRebuys * config.rebuyStack) +
        (config.allowAddon ? config.addonStack : 0);
    int totalMaxChips = maxChipsPerPlayer * config.numPlayers;

    // Calculate prize pool estimates
    int buyinEquivalent = 100; // Assuming $100 buyin for calculation
    int minPrizePool = buyinEquivalent * config.numPlayers;
    int maxPrizePool = minPrizePool +
        (buyinEquivalent * config.maxRebuys * config.numPlayers) +
        (config.allowAddon ? buyinEquivalent * config.numPlayers : 0);

    // Calculate number of blind levels
    int numLevels = (int) Math.ceil(
        Math.log(config.numPlayers / 4.0) /
            Math.log(1.0 / (1.0 - PLAYERS_ELIMINATED_PER_LEVEL))
    );

    // Calculate level duration
    int levelDuration = (int) Math.floor((config.desiredDurationHours * 60) / numLevels);

    // Calculate starting small blind (1/200 of starting stack)
    int smallBlind = (int) Math.ceil(config.startingStack / 200.0);

    // Generate blind schedule
    for (int level = 0; level < numLevels; level++) {
      // Increase blinds by approximately 50% each level
      if (level > 0) {
        smallBlind = (int) Math.ceil(smallBlind * 1.5);
      }

      // Add antes starting from level 4
      int ante = 0;
      if (level >= 3) {
        ante = (int) Math.ceil(smallBlind / 4.0);
      }

      // Calculate running time
      int runningTimeMinutes = level * levelDuration;
      String runningTime = String.format("%dh %dm",
          runningTimeMinutes / 60,
          runningTimeMinutes % 60);

      // Determine rebuy and addon availability
      boolean rebuysAllowed = level <= config.rebuyEndLevel;
      boolean addonAvailable = config.allowAddon && level == config.rebuyEndLevel;

      // Create blind level
      BlindLevel blindLevel = new BlindLevel(
          level + 1,
          smallBlind,
          smallBlind * 2,
          ante,
          levelDuration,
          runningTime,
          rebuysAllowed,
          addonAvailable
      );

      // Add special notes
      if (level == 0) {
        blindLevel.specialNotes = "Tournament Start";
      } else if (level == config.rebuyEndLevel) {
        blindLevel.specialNotes = "Rebuy Period Ends" +
            (config.allowAddon ? " - Add-on Available" : "");
      }

      blindSchedule.add(blindLevel);
    }

    TournamentStats stats = new TournamentStats(
        totalMaxChips,
        minPrizePool,
        maxPrizePool
    );

    return new ScheduleResult(blindSchedule, stats);
  }

  public static void printSchedule(ScheduleResult result) {
    System.out.println("\nPoker Tournament Blind Schedule");
    System.out.println("=".repeat(100));
    System.out.printf("%-6s %-12s %-12s %-8s %-10s %-15s %-20s%n",
        "Level", "Small Blind", "Big Blind", "Ante", "Duration", "Running Time", "Notes");
    System.out.println("-".repeat(100));

    for (BlindLevel level : result.schedule) {
      StringBuilder notes = new StringBuilder();
      if (level.rebuysAllowed) notes.append("Rebuys ");
      if (level.addonAvailable) notes.append("Add-on ");
      if (!level.specialNotes.isEmpty()) notes.append(level.specialNotes);

      System.out.printf("%-6d %-12d %-12d %-8d %-10s %-15s %-20s%n",
          level.level,
          level.smallBlind,
          level.bigBlind,
          level.ante,
          level.durationMinutes + "min",
          level.runningTime,
          notes.toString());
    }

    // Print tournament statistics
    System.out.println("\nTournament Statistics");
    System.out.println("=".repeat(50));
    System.out.printf("Maximum Chips in Play: %,d%n", result.stats.estimatedMaxChips);
    System.out.printf("Estimated Prize Pool: $%,d - $%,d%n",
        result.stats.estimatedMinPrizePool,
        result.stats.estimatedMaxPrizePool);
  }

  public static void main(String[] args) {
    // Example usage
    TournamentConfig config = new TournamentConfig(
        100,    // numPlayers
        10000,  // startingStack
        2,      // maxRebuys
        true,   // allowAddon
        10000,  // rebuyStack
        20000,  // addonStack
        4,      // rebuyEndLevel
        6.0     // desiredDurationHours
    );

    ScheduleResult result = calculateBlindSchedule(config);
    printSchedule(result);
  }
}
